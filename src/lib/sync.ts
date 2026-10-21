/**
 * Sync agent — replicates local SQLite ↔ cloud Postgres (VPS).
 *
 * Model: outbox + cursor.
 *  - Local writes land in `sync_outbox`; push sends them to /api/v1/push,
 *    pull applies server changes into SQLite by uuid.
 *  - `sync_state` keeps the pull cursor.
 *
 * Rules (mirrors server/migrations/012_sync.sql):
 *  - Financial rows are insert-only → uuid-idempotent, retries are safe.
 *  - Editable entities use last-writer-wins by updated_at.
 */

import { getDeviceToken, getCloudBase, setDeviceToken } from "./cloud";

/** Tables the agent replicates (mirrors SYNC_TABLES in server/index.js). */
export const SYNC_TABLES = [
  "products", "services", "customers", "vehicles", "vendors",
  "orders", "order_items", "expenses", "accounts", "account_transactions",
  "purchase_orders", "vendor_payables", "reminders", "inspections",
  "cash_movements", "reservations",
  // The khata + road bookings (migration 014). Mirrors SYNC_TABLES in
  // server/index.js; commitment_items is still missing there (no shop_id).
  "party_ledger", "commitments",
  // The stock ledger (migration 025). Pulled so the desktop can show why an
  // item's stock is what it is, and reconcile products.stock_qty against
  // SUM(qty) without needing the server.
  "stock_movements",
  // Price lists (migration 028). Replicated so the POS can price a customer
  // from their own list while offline, instead of falling back to the tier
  // columns and quietly charging walk-in prices to a wholesale customer.
  "price_lists", "price_list_items", "party_price_lists", "party_prices",
  // Customer groups (migration 031). The group decides the price tier, so an
  // offline till that lacks it would bill a wholesale customer at retail.
  "customer_groups",
  // Units (migration 032) - read-mostly, replicated so pack sizes can be shown
  // as configured data rather than re-parsed from a string on every device.
  "units", "unit_conversions",
  // Warehouses and their locations (migration 033). stock_levels is NOT here:
  // it is a derived cache, and stock_movements already replicates. Replicating
  // both would hand the desktop two truths that can arrive out of step - the
  // exact disagreement migration 033 exists to prevent.
  "warehouses", "warehouse_locations",
] as const;

const OUTBOX_SCHEMA = `
CREATE TABLE IF NOT EXISTS sync_outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  row_uuid TEXT NOT NULL,
  op TEXT NOT NULL DEFAULT 'insert',
  payload TEXT NOT NULL,
  queued_at TEXT DEFAULT (datetime('now')),
  attempts INTEGER DEFAULT 0,
  last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_outbox_queued ON sync_outbox(queued_at);
CREATE TABLE IF NOT EXISTS sync_state (
  key TEXT PRIMARY KEY,
  value TEXT
);
INSERT OR IGNORE INTO sync_state (key, value) VALUES ('pull_cursor', '0');
`;

let schemaReady = new WeakMap<object, Promise<void>>();
// Prevent overlapping sync rounds on the same database handle. An overlapping
// pull could otherwise reuse a stale cursor or interleave outbox acknowledgments.
const activeSyncs = new WeakMap<object, Promise<{ pushed: number; rejected: number; pulled: number }>>();
function ensureOutbox(db: any): Promise<void> {
  const existing = schemaReady.get(db);
  if (existing) return existing;
  const ready: Promise<void> = Promise.resolve(db.execute(OUTBOX_SCHEMA))
    .then(() => undefined)
    .catch((e: unknown) => { schemaReady.delete(db); throw e; });
  schemaReady.set(db, ready);
  return ready;
}

export type SyncStatus = "offline" | "idle" | "syncing" | "error";
let status: SyncStatus = "offline";
let lastError = "";
let lastSyncAt: string | null = null;
const listeners = new Set<(s: SyncStatus, err: string) => void>();

export function getSyncStatus(): { status: SyncStatus; lastError: string; lastSyncAt: string | null } {
  return { status, lastError, lastSyncAt };
}

export function onSyncStatus(fn: (s: SyncStatus, err: string) => void): () => void {
  listeners.add(fn);
  return () => { listeners.delete(fn); };
}

function setStatus(s: SyncStatus, err = "") {
  status = s;
  lastError = err;
  if (s === "idle") lastSyncAt = new Date().toISOString();
  for (const fn of listeners) { try { fn(s, err); } catch { /* listener error */ } }
}

/** One sync round: push outbox → pull changes. Returns row counts. */
export function syncNow(db: any): Promise<{ pushed: number; rejected: number; pulled: number }> {
  const existing = activeSyncs.get(db);
  if (existing) return existing;
  const task = runSyncRound(db);
  activeSyncs.set(db, task);
  void task.finally(() => { if (activeSyncs.get(db) === task) activeSyncs.delete(db); }).catch(() => {});
  return task;
}

async function runSyncRound(db: any): Promise<{ pushed: number; rejected: number; pulled: number }> {
  const base = getCloudBase();
  if (!base) throw new Error("Cloud URL not configured");
  if (!getDeviceToken()) throw new Error("Device pairing required before cloud sync");
  await ensureOutbox(db);
  setStatus("syncing");

  let pushed = 0, rejected = 0, pulled = 0;
  try {
    // ── push ─────────────────────────────────────────────────────
    const outbox = await db.select("SELECT id, table_name, row_uuid, op, payload FROM sync_outbox ORDER BY id LIMIT 200");
    if (outbox.length) {
      const changes = outbox.map((r: any) => ({
        table: r.table_name, uuid: r.row_uuid, op: r.op, operationId: String(r.id), row: JSON.parse(r.payload),
      }));
      const res = await fetch(`${base}/api/v1/push`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify({ changes }),
      });
      if (!res.ok) throw new Error(`Push failed: ${res.status}`);
      const data = await res.json();
      if (!data || !Array.isArray(data.applied) || !Array.isArray(data.rejected)) {
        throw new Error("Invalid synchronization acknowledgment response");
      }
      const sentIds = new Set<string>(changes.map((change: { operationId: string }) => change.operationId));
      pushed = 0;
      rejected = 0;
      // A receipt must acknowledge this exact outbox operation, not merely a
      // UUID shared by several edits or tables. Legacy UUID-only receipts are
      // deliberately retained for retry instead of risking silent data loss.
      const appliedIds = new Set<string>((data.applied ?? [])
        .filter((x: any) => x.operationId !== undefined && x.operationId !== null)
        .map((x: any) => String(x.operationId))
        .filter((id: string) => sentIds.has(id)));
      const rejectedIds = new Set<string>((data.rejected ?? [])
        .filter((x: any) => x.operationId !== undefined && x.operationId !== null)
        .map((x: any) => String(x.operationId))
        .filter((id: string) => sentIds.has(id)));
      for (const r of outbox) {
        const operationId = String(r.id);
        if (appliedIds.has(operationId) && !rejectedIds.has(operationId)) {
          await db.execute("DELETE FROM sync_outbox WHERE id = $1", [r.id]);
          pushed++;
        } else {
          if (rejectedIds.has(operationId)) rejected++;
          await db.execute(
            "UPDATE sync_outbox SET attempts = attempts + 1, last_error = $2 WHERE id = $1",
            [r.id, rejectedIds.has(operationId) ? "rejected by server" : "missing or ambiguous operation receipt"]
          );
        }
      }
    }

    // ── pull ─────────────────────────────────────────────────────
    const state = await db.select("SELECT value FROM sync_state WHERE key = 'pull_cursor'");
    const cursor = Number(state[0]?.value ?? 0);
    const res = await fetch(`${base}/api/v1/pull?since=${cursor}`, { headers: authHeaders() });
    if (!res.ok) throw new Error(`Pull failed: ${res.status}`);
    const data = await res.json();
    // Validate the complete response before touching the local database.
    const nextCursor = Number(data.cursor);
    if (!Number.isSafeInteger(cursor) || cursor < 0 ||
        !Number.isSafeInteger(nextCursor) || nextCursor < cursor) {
      throw new Error("Invalid or regressing server sync cursor");
    }
    if (data.changes !== undefined && (data.changes === null || typeof data.changes !== "object" ||
        Array.isArray(data.changes))) throw new Error("Invalid server change batch");
    const statements: Array<{ sql: string; values: unknown[] }> = [];
    for (const [table, rows] of Object.entries(data.changes ?? {}) as [string, any[]][]) {
      if (!(SYNC_TABLES as readonly string[]).includes(table))
        throw new Error(`Unexpected sync table: ${table}`);
      if (!Array.isArray(rows)) throw new Error("Invalid sync rows");
      for (const row of rows) {
        if (!row || typeof row !== "object" || Array.isArray(row))
          throw new Error("Invalid sync row");
        const cols = Object.keys(row).filter((col) => col !== "id");
        if (!cols.includes("uuid") || typeof row.uuid !== "string" || !row.uuid)
          throw new Error("Sync row missing UUID");
        if (cols.some((col) => !/^[a-z][a-z0-9_]*$/i.test(col)))
          throw new Error("Invalid sync column");
        const vals = cols.map((col) => row[col]);
        const placeholders = cols.map((_, i) => `$${i + 1}`);
        const updateSet = cols.filter((col) => col !== "uuid")
          .map((col) => `${col} = excluded.${col}`).join(", ");
        statements.push({
          sql: `INSERT INTO ${table} (${cols.join(", ")}) VALUES (${placeholders.join(", ")})
                ON CONFLICT(uuid) DO ${updateSet ? `UPDATE SET ${updateSet}` : "NOTHING"}`,
          values: vals,
        });
      }
    }
    // Record application and cursor advancement are one atomic unit.
    // Any failed statement must leave both records and cursor unchanged.
    await db.execute("BEGIN IMMEDIATE");
    try {
      for (const statement of statements) {
        await db.execute(statement.sql, statement.values);
        pulled++;
      }
      await db.execute("UPDATE sync_state SET value = $1 WHERE key = 'pull_cursor'", [String(nextCursor)]);
      await db.execute("COMMIT");
    } catch (error) {
      await db.execute("ROLLBACK");
      pulled = 0;
      throw error;
    }
    setStatus("idle");
  } catch (e: any) {
    setStatus("error", e?.message ?? String(e));
    throw e;
  }
  return { pushed, rejected, pulled };
}

function authHeaders(): Record<string, string> {
  const t = getDeviceToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
}

/** Pair this device with a one-time code and store the token. */
export async function pairDevice(code: string, name: string): Promise<void> {
  const base = getCloudBase();
  if (!base) throw new Error("Cloud URL not configured");
  const res = await fetch(`${base}/api/v1/devices/pair`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, name }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || "Pairing failed");
  }
  const data = await res.json();
  setDeviceToken(data.token);
}

/** Background loop: sync every 2 minutes while configured. */
let timer: ReturnType<typeof setInterval> | null = null;
export function startSyncLoop(getDb: () => Promise<any>): void {
  if (timer) return;
  timer = setInterval(async () => {
    if (!getCloudBase() || !getDeviceToken()) return;
    try { await syncNow(await getDb()); } catch { /* status already set */ }
  }, 2 * 60 * 1000);
}
