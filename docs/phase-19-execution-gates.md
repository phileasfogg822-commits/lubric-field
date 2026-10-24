# Phase 19 — Inventory Ledger & Stock Ops (Roadmap V2)

Branch: `phase-19-inventory-ledger` (from the Phase 18
candidate, `phase-18-quotations-sales-orders` at `42ddb48`).
Draft PR to follow via the connector. Packages are accepted
only with green-CI proof on the exact SHA, as in Phases 12–18.

## Authoritative scope

Master spec §180 (inventory ledger: stock is derived from
movements, never from `item.quantity` alone), §181
(immutability: corrections are reversing movements), §31
(inventory screen: computed value / item / low-stock cards and
filters), §32 (stock status), §33 (minimum stock & reorder
level), §36 (stock adjustment, reason required), §37 (stock
count with variance), §35 (stock transfers, paired movements),
§30 (serial number tracking), §175 (negative-stock policy as a
business setting). This phase also consumes the Phase 17.4
posting contract's `StockMovement` vocabulary — it is the
contract's first real producer — and closes the Phase 16
deferrals (§30 serials, §35 transfers).

Movements live in the Phase 12 operational store as
"stock_movements" records — already in the sync whitelist AND
INSERT_ONLY in the sync queue, so the ledger is append-only by
platform rule. Transfer headers ("stock_transfers") and serial
records are NOT whitelisted → local-only, the numbering /
timeline posture from Phase 18; stock truth still syncs
because it lives in the movements.

Standing decisions, pinned at branch time (from the phase
prep): the balance engine takes each item's Phase 15
`openingStock` datum as its starting constant — no OPENING
movement is ever fabricated on first read (fake history). The
16.4 stored batch quantity stays a datum and is never summed
into availability; batch balances derive from movements
carrying a batchId. §213 stock reservation is deferred to
Phases 21/22 (it binds to orders/invoices), recorded here, not
silently dropped. The negative-stock default at the UI is Warn
until a settings home exists; the engine takes the policy as
an input and never hard-codes one behaviour.

## Package split

- **19.1** Movement model + codec: the typed stock-movement
  record over "stock_movements", aligned with the 17.4
  vocabulary, + the typed ViewModel surface and the
  `postMovement` write API later packages post through.
- **19.2** Ledger engine (domain/inventory): balances per
  item (warehouse / batch dimensions) from movements, §32
  status from balance + §33 levels, §175 policy as an input.
- **19.3** Inventory screens: the typed §31 screen (computed
  cards, filters, per-item movement history) replacing the
  raw Phase 12 screen.
- **19.4** Adjustments + stock count (§36/§37): adjustment
  form posting movements; count mode with variance → adjust.
- **19.5** Transfers (§35): header + lines, status flow,
  paired TRANSFER_OUT / TRANSFER_IN movements.
- **19.6** Serials (§30): serial registry records, receive /
  adjust flows, per-item serial list. Sale-time serial removal
  lands with Phase 21 document posting.
- **19.7** Phase gate: evidence sweep + close-out +
  Phase 20 prep.

## Package 19.1 — Movement model + codec

Status: NOT STARTED.

## Package 19.2 — Ledger engine

Status: NOT STARTED.

## Package 19.3 — Inventory screens

Status: NOT STARTED.

## Package 19.4 — Adjustments + stock count

Status: NOT STARTED.

## Package 19.5 — Transfers

Status: NOT STARTED.

## Package 19.6 — Serials

Status: NOT STARTED.

## Package 19.7 — Phase gate

Status: NOT STARTED.
