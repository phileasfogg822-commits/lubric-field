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
      if (mode === 'conflict') return { rowCount: 0 };
      return { rowCount: 1 };
    }
    if (statement.startsWith('SELECT shop_id FROM')) return { rows: [{ shop_id: mode === 'other-shop' ? 99 : 7 }] };
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
  Set, String, Date, Object, console,
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
