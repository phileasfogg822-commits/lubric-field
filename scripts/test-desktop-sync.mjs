import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import ts from "typescript";

// Execute the production TypeScript, not a rewritten copy of its sync algorithm.
const source = fs.readFileSync(new URL("../src/lib/sync.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  reportDiagnostics: true,
});
assert.equal(compiled.diagnostics?.length ?? 0, 0);
const module = { exports: {} };
let response;
const context = {
  module, exports: module.exports, console, Date, WeakMap, Set, Promise,
  setInterval, clearInterval,
  require: () => ({
    getCloudBase: () => "https://synthetic.example",
    getDeviceToken: () => "test-device-token",
    setDeviceToken: () => {},
  }),
  fetch: async (url) => {
    const result = url.endsWith("/push") ? response.push : response.pull;
    return { ok: true, json: async () => result };
  },
};
vm.runInNewContext(compiled.outputText, context, { filename: "src/lib/sync.ts" });
const { syncNow } = module.exports;

function database(rows = [{ id: 1, table_name: "products", row_uuid: "p1", op: "insert", payload: '{"uuid":"p1"}' }]) {
  const state = { rows: [...rows], cursor: "0", deletes: [], retries: [], statements: [] };
  return {
    state,
    async select(sql) {
      if (sql.includes("FROM sync_outbox")) return state.rows;
      if (sql.includes("FROM sync_state")) return [{ value: state.cursor }];
      throw new Error("Unexpected SELECT " + sql);
    },
    async execute(sql, params = []) {
      state.statements.push(sql);
      if (sql.startsWith("DELETE FROM sync_outbox")) state.deletes.push(params[0]);
      if (sql.startsWith("UPDATE sync_outbox")) state.retries.push(params[0]);
      if (sql.startsWith("UPDATE sync_state")) state.cursor = params[0];
    },
  };
}

async function check(name, push, expectedDeletes, expectedRetries, expectedPushed, expectedRejected) {
  response = { push, pull: { cursor: 1, changes: {} } };
  const db = database();
  const actual = await syncNow(db);
  assert.deepEqual(db.state.deletes, expectedDeletes, name + " deletes");
  assert.deepEqual(db.state.retries, expectedRetries, name + " retries");
  assert.equal(actual.pushed, expectedPushed, name + " pushed");
  assert.equal(actual.rejected, expectedRejected, name + " rejected");
  assert.equal(db.state.cursor, "1", name + " cursor");
  console.log("PASS", name);
}
await check("matching operation receipt", { applied: [{ operationId: "1" }], rejected: [] }, [1], [], 1, 0);
await check("missing receipt retains outbox", { applied: [], rejected: [] }, [], [1], 0, 0);
await check("unknown receipt retains outbox", { applied: [{ operationId: "999" }], rejected: [] }, [], [1], 0, 0);
await check("legacy UUID-only receipt retains outbox", { applied: [{ uuid: "p1" }], rejected: [] }, [], [1], 0, 0);
await check("rejected receipt retains outbox", { applied: [], rejected: [{ operationId: "1" }] }, [], [1], 0, 1);
await check("conflicting receipts retain outbox", { applied: [{ operationId: "1" }], rejected: [{ operationId: "1" }] }, [], [1], 0, 1);
await check("duplicate matching receipt counted once", { applied: [{ operationId: "1" }, { operationId: "1" }], rejected: [] }, [1], [], 1, 0);
for (const bad of [null, {}, { applied: [], rejected: null }]) {
  response = { push: bad, pull: { cursor: 1, changes: {} } };
  const db = database();
  await assert.rejects(syncNow(db), /Invalid synchronization acknowledgment response/);
  assert.deepEqual(db.state.deletes, []);
  console.log("PASS malformed receipt");
}
for (const cursor of [-1, "NaN", 1.5]) {
  response = { push: { applied: [], rejected: [] }, pull: { cursor, changes: {} } };
  const db = database([]);
  await assert.rejects(syncNow(db), /Invalid or regressing server sync cursor/);
  assert.equal(db.state.cursor, "0");
  console.log("PASS invalid cursor", cursor);
}

for (const scenario of [
  { name: "invalid incoming table", pull: { cursor: 2, changes: { unknown_table: [{ uuid: "x" }] } } },
  { name: "invalid incoming row", pull: { cursor: 2, changes: { products: [{ name: "no uuid" }] } } },
  { name: "invalid incoming batch", pull: { cursor: 2, changes: [] } },
  { name: "regressing cursor", pull: { cursor: -1, changes: {} } },
]) {
  response = { push: { applied: [], rejected: [] }, pull: scenario.pull };
  const db = database([]);
  await assert.rejects(syncNow(db));
  assert.equal(db.state.cursor, "0", scenario.name + " must preserve cursor");
  assert.ok(!db.state.statements.some((sql) => sql.startsWith("INSERT INTO products")), scenario.name);
  console.log("PASS", scenario.name);
}

for (const scenario of [
  { name: "nested object sync value", pull: { cursor: 2, changes: { products: [{ uuid: "x", name: { nested: "unsafe" } }] } } },
  { name: "nested array sync value", pull: { cursor: 2, changes: { products: [{ uuid: "x", name: ["unsafe"] }] } } },
]) {
  response = { push: { applied: [], rejected: [] }, pull: scenario.pull };
  const db = database([]);
  await assert.rejects(syncNow(db), /Invalid sync value/);
  assert.equal(db.state.cursor, "0");
  assert.ok(!db.state.statements.includes("BEGIN IMMEDIATE"));
  console.log("PASS", scenario.name);
}

// Fault injection: a database failure after one applied row must issue ROLLBACK,
// never COMMIT or a cursor advance. This is a protocol test with a test double,
// NOT evidence of native Tauri SQLite connection-pool atomicity.
response = {
  push: { applied: [], rejected: [] },
  pull: { cursor: 3, changes: { products: [{ uuid: "a", name: "A" }, { uuid: "b", name: "B" }] } },
};
const failedDb = database([]);
let applied = 0;
const originalExecute = failedDb.execute.bind(failedDb);
failedDb.execute = async (sql, params) => {
  if (sql.startsWith("INSERT INTO products") && ++applied === 2) {
    throw new Error("synthetic disk failure");
  }
  return originalExecute(sql, params);
};
await assert.rejects(syncNow(failedDb), /synthetic disk failure/);
assert.ok(failedDb.state.statements.includes("BEGIN IMMEDIATE"));
assert.ok(failedDb.state.statements.includes("ROLLBACK"));
assert.ok(!failedDb.state.statements.includes("COMMIT"));
assert.equal(failedDb.state.cursor, "0");
console.log("PASS failed incoming batch requests rollback and retains cursor");

response = { push: { applied: [], rejected: [] }, pull: { cursor: 3, changes: { products: [{ uuid: "a", name: "A" }] } } };
const retryDb = database([]);
const retry = await syncNow(retryDb);
assert.equal(retry.pulled, 1);
assert.equal(retryDb.state.cursor, "3");
assert.ok(retryDb.state.statements.includes("COMMIT"));
console.log("PASS successful incoming retry commits cursor");
