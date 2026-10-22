import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Exercise the actual production push handler with a deterministic PostgreSQL
// protocol double. This checks receipt and transaction control-flow without
// pretending that a mock replaces a real PostgreSQL/RLS integration test.
const source = fs.readFileSync(new URL('./index.js', import.meta.url), 'utf8');
const start = source.indexOf("app.post('/api/v1/push',");
const end = source.indexOf('// ─── App update distribution', start);
assert.ok(start >= 0 && end > start, 'production push handler located');
const handlerSource = source.slice(start, end);
const tableStart = source.indexOf('const SYNC_TABLES =');
const tableEnd = source.indexOf('\n};', tableStart);
assert.ok(tableStart >= 0 && tableEnd > tableStart, 'production sync table configuration located');
const SYNC_TABLES = vm.runInNewContext(source.slice(tableStart, tableEnd + 3).replace('const SYNC_TABLES =', '(').replace(/;\s*$/, ')'));
let handler;
const sql = [];
let mode = '';
let posted = 0;
const client = {
  async query(statement, params = []) {
    sql.push({ statement, params });
    if (statement.includes('information_schema.columns')) {
      return { rows: ['uuid','shop_id','amount','updated_at','posted_at','taken_by_device_id'].map(column_name => ({ column_name })) };
    }
    if (statement.startsWith('INSERT INTO')) {
      if (mode === 'statement-error') throw new Error('synthetic constraint violation');
      if ((mode === 'conflict' || mode === 'other-shop' || mode === 'mismatched-replay')) return { rowCount: 0 };
      return { rowCount: 1 };
    }
    if (statement.startsWith('SELECT shop_id')) return { rows: [{ shop_id: mode === 'other-shop' ? 99 : 7, replay_matches: mode !== 'mismatched-replay' }] };
    if (statement.startsWith('UPDATE products SET deleted_at')) return { rowCount: 0 };
    if (statement.startsWith('SELECT id FROM cash_movements')) return { rows: [{ id: 1 }] };
    return { rowCount: 1, rows: [] };
  },
  release() {},
};
const app = { post(path, callback) { if (path === '/api/v1/push') handler = callback; } };
vm.runInNewContext(handlerSource, {
  app, pool: { connect: async () => client },
  query: async () => ({ rowCount: 1 }),
  postFieldCollection: async () => { posted++; },
  SYNC_TABLES, Set, String, Date, Object, console,
});
assert.equal(typeof handler, 'function');

async function run(changes, scenario) {
  mode = scenario; sql.length = 0; posted = 0;
  let status = 200, body;
  const res = { status(code) { status = code; return this; }, json(value) { body = value; return this; } };
  await handler({ device: { id: 5, shop_id: 7 }, body: { changes } }, res);
  return { status, body, statements: sql.map(x => x.statement), queries: [...sql], posted };
}
const change = (table, uuid, row, op = 'insert') => ({ table, uuid, row, op, operationId: 'op-1' });

let result = await run([change('party_ledger', 'ledger-1', { amount: 100 })], 'conflict');
assert.equal(result.body.applied.length, 1);
assert.equal(result.body.rejected.length, 0);
assert.ok(result.statements.some(x => x.startsWith('RELEASE SAVEPOINT')));
console.log('PASS same-shop immutable replay acknowledges operation');

result = await run([change('party_ledger', 'ledger-1', { amount: 100 })], 'other-shop');
assert.equal(result.body.rejected.length, 1);
assert.equal(result.body.applied.length, 0);
console.log('PASS cross-shop UUID conflict rejected');

result = await run([change('products', 'p-1', { amount: 100 })], 'statement-error');
assert.equal(result.status, 200);
assert.equal(result.body.rejected.length, 1);
assert.ok(result.statements.some(x => x.startsWith('ROLLBACK TO SAVEPOINT')));
assert.ok(result.statements.includes('COMMIT'));
console.log('PASS rejected statement rolls back savepoint before commit');

result = await run([change('products', 'p-1', {}, 'delete')], '');
assert.equal(result.body.rejected.length, 1);
assert.equal(result.body.applied.length, 0);
console.log('PASS missing or cross-shop delete is not acknowledged');

result = await run([change('cash_movements', 'cash-1', { amount: 30, posted_at: 'spoofed', taken_by_device_id: 999 })], '');
const insert = result.queries.find(x => x.statement.startsWith('INSERT INTO cash_movements'));
assert.ok(insert);
assert.ok(!insert.statement.includes('posted_at'));
assert.ok(!insert.params.includes(999));
assert.ok(insert.params.includes(5));
assert.equal(result.posted, 1);
console.log('PASS cash posting and attribution cannot be spoofed');

for (const invalid of [
  change('constructor', 'bad-1', { amount: 1 }),
  change('products', 123, { amount: 1 }),
  change('products', 'p-1', [], 'insert'),
  change('products', 'p-1', { amount: 1 }, 'truncate'),
]) {
  const response = await run([invalid], '');
  assert.equal(response.status, 200);
  assert.equal(response.body.rejected.length, 1);
  assert.equal(response.body.applied.length, 0);
  assert.ok(!response.statements.some(x => x.startsWith('INSERT INTO')));
}
console.log('PASS malformed and inherited-table changes rejected without SQL');

const batch = await run([
  change('products', 'bad', { amount: 1 }),
  change('party_ledger', 'good', { amount: 1 }),
], 'statement-error');
assert.equal(batch.body.rejected.length, 2);
assert.equal(batch.body.applied.length, 0);
assert.equal(batch.statements.filter(x => x.startsWith('ROLLBACK TO SAVEPOINT')).length, 2);
assert.ok(batch.statements.includes('COMMIT'));
console.log('PASS multiple rejected changes do not abort transaction');

result = await run([change('party_ledger', 'ledger-1', { amount: 999 })], 'mismatched-replay');
assert.equal(result.body.applied.length, 0);
assert.equal(result.body.rejected.length, 1);
assert.ok(result.queries.some(x => x.statement.includes('amount IS NOT DISTINCT FROM')));
console.log('PASS immutable UUID replay with changed amount is rejected');
