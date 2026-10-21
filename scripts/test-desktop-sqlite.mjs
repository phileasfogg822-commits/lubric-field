import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { mkdtempSync, rmSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import vm from "node:vm";
import ts from "typescript";

// Real on-disk SQLite tests of the production sync SQL and rollback sequence.
// This adapter uses ONE SQLite connection; native Tauri plugin pooling must be
// separately verified before claiming end-to-end Tauri transaction safety.
const dir = mkdtempSync(join(tmpdir(), "lubric-sync-"));
const file = join(dir, "test.sqlite");
const db = new DatabaseSync(file);
let pull;
const source = readFileSync(new URL("../src/lib/sync.ts", import.meta.url), "utf8");
const code = ts.transpileModule(source, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
}}).outputText;
const module = { exports: {} };
vm.runInNewContext(code, {
  module, exports: module.exports, WeakMap, Set, Promise, Date, console,
  setInterval, clearInterval,
  require: () => ({
    getCloudBase: () => "https://synthetic.example",
    getDeviceToken: () => "synthetic-token",
    setDeviceToken: () => {},
  }),
  fetch: async () => ({ ok: true, json: async () => pull }),
}, { filename: "src/lib/sync.ts" });
const { syncNow } = module.exports;
// Tauri SQL accepts $1-style positional placeholders. Node node:sqlite binds
// positional values reliably with ? placeholders; translate in the test adapter.
function preparePositional(conn, sql, values) {
  const ordered = [];
  const normalized = sql.replace(/\\$(\\d+)/g, (_, index) => {
    ordered.push(values[Number(index) - 1]);
    return "?";
  });
  if (values.length && !ordered.length && !normalized.includes("?")) {
    throw new Error("SQL test adapter found values without recognized placeholders");
  }
  return { statement: conn.prepare(normalized), args: ordered.length ? ordered : values };
}
assert.deepEqual(preparePositional(db, "SELECT $2, $1", ["one", "two"]).args, ["two", "one"]);
console.log("PASS SQLite numbered placeholder translation");
const adapter = {
  async select(sql, values = []) {
    return preparePositional(db, sql, values).statement.all(...preparePositional(db, sql, values).args);
  },
  async execute(sql, values = []) {
    if (sql.includes("CREATE TABLE IF NOT EXISTS sync_outbox")) {
      db.exec(sql);
      return;
    }
    if (values.length) preparePositional(db, sql, values).statement.run(...preparePositional(db, sql, values).args);
    else db.exec(sql);
  },
};
try {
  db.exec("CREATE TABLE products (uuid TEXT PRIMARY KEY, name TEXT NOT NULL);");
  pull = { cursor: 1, changes: { products: [{ uuid: "first", name: "A" }] } };
  assert.equal((await syncNow(adapter)).pulled, 1);
  assert.equal(db.prepare("SELECT value FROM sync_state WHERE key = 'pull_cursor'").get().value, "1");
  db.close();
  const reopened = new DatabaseSync(file);
  assert.equal(reopened.prepare("SELECT name FROM products WHERE uuid = 'first'").get().name, "A");
  assert.equal(reopened.prepare("SELECT value FROM sync_state WHERE key = 'pull_cursor'").get().value, "1");
  reopened.close();
  console.log("PASS SQLite durable commit survives reopen");

  const conn = new DatabaseSync(file);
  const live = {
    select: async (sql, values = []) => preparePositional(conn, sql, values).statement.all(...preparePositional(conn, sql, values).args),
    execute: async (sql, values = []) => {
      if (sql.includes("CREATE TABLE IF NOT EXISTS sync_outbox")) return conn.exec(sql);
      if (values.length) return preparePositional(conn, sql, values).statement.run(...preparePositional(conn, sql, values).args);
      return conn.exec(sql);
    },
  };
  pull = { cursor: 2, changes: { products: [
    { uuid: "second", name: "B" },
    { uuid: "third", name: null },
  ] } };
  await assert.rejects(syncNow(live), /NOT NULL constraint failed/);
  assert.equal(conn.prepare("SELECT COUNT(*) AS n FROM products WHERE uuid = 'second'").get().n, 0);
  assert.equal(conn.prepare("SELECT value FROM sync_state WHERE key = 'pull_cursor'").get().value, "1");
  conn.close();
  console.log("PASS SQLite real rollback preserves rows and cursor");
  const afterFailure = new DatabaseSync(file);
  assert.equal(afterFailure.prepare("SELECT COUNT(*) AS n FROM products WHERE uuid = 'second'").get().n, 0);
  assert.equal(afterFailure.prepare("SELECT value FROM sync_state WHERE key = 'pull_cursor'").get().value, "1");
  afterFailure.close();
  console.log("PASS SQLite rollback survives reopen");
  const retryDb = new DatabaseSync(file);
  const retryAdapter = {
    select: async (sql, values = []) => preparePositional(retryDb, sql, values).statement.all(...preparePositional(retryDb, sql, values).args),
    execute: async (sql, values = []) => {
      if (sql.includes("CREATE TABLE IF NOT EXISTS sync_outbox")) return retryDb.exec(sql);
      return values.length ? preparePositional(retryDb, sql, values).statement.run(...preparePositional(retryDb, sql, values).args) : retryDb.exec(sql);
    },
  };
  pull = { cursor: 2, changes: { products: [
    { uuid: "second", name: "B" },
    { uuid: "third", name: "C" },
  ] } };
  assert.equal((await syncNow(retryAdapter)).pulled, 2);
  assert.equal(retryDb.prepare("SELECT COUNT(*) AS n FROM products").get().n, 3);
  assert.equal(retryDb.prepare("SELECT value FROM sync_state WHERE key = 'pull_cursor'").get().value, "2");
  retryDb.close();
  console.log("PASS SQLite retry applies complete batch and advances cursor");
} finally {
  try { db.close(); } catch {}
  rmSync(dir, { recursive: true, force: true });
}
