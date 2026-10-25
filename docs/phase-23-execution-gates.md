# Phase 23 execution gates — Purchasing / Receiving / Payables / Debit Notes

Branch `phase-23-purchasing-payables`, base
`phase-22-payments-receivables`. Packages land on this
branch one at a time, each accepted only with green CI
on its exact head. This doc is the ledger; the design
detail lives in
`agent_notes/phase-23-prep.md` (goal workspace).

## Scope

The vendor side of the ledger, mirroring Phase 22's
customer side: typed purchase/payables records, goods
receipt as a purchase-status transition, vendor
payments against payables, and debit notes for returns
to suppliers. Payables ride the same `party_ledger`
table as receivables, with PAYABLE effects as the
ledger truth.

## Settled pins (from code, 2026-10-24)

- `purchase_orders` and `vendor_payables` are already
  in SUPPORTED_TABLES — but NOT INSERT_ONLY (only
  account_transactions, cash_movements,
  stock_movements, party_ledger are), so they are
  mutable sync tables already, which fits records
  that change status (DRAFT to RECEIVED to PAID).
- `PartyBalanceDirection` already has PAYABLE
  alongside RECEIVABLE; `PartyLedgerEffect` is a
  signed amount on the same `party_ledger` table —
  payables ride the same effect type/table, no
  second ledger.
- `domain/purchase/Purchase.kt` already has
  `PurchaseInvoice` with `paidAmount`, derived
  `balanceDue`, `dueDate`, and
  DRAFT/RECEIVED/PAID/PARTIALLY_PAID/OVERDUE/CANCELLED
  — receiving and payment status already share one
  record shape, mirroring sales.

## Gap (checked 2026-10-24)

`domain/purchase` contains only `Purchase.kt` — no
receiving/goods-receipt model, debit-note type, codec,
posting builder, or tests. The whitelisted tables are
unwired: no model/ViewModel references
`vendor_payables` at all; `purchase_orders` is only
used generically (PurchasesViewModel observes/saves
OperationalRecord, no typed invoice/receiving/
debit-note surface).

## Supervisor decisions (2026-10-24, main-chat
delegation — Umar: "you decide"; engineering calls
only, within standing authorization; merges/releases
still need Umar's tap)

1. Derive-vs-store: DERIVE, mirroring sales. Stored
   `PurchaseInvoice.paidAmount` is treated as a CACHED
   value derived from payment records, recomputed on
   payment/credit events, with the derivation as the
   authority.
2. `vendor_payables` IS the payables record (typed
   invoice/receiving/debit-note surface), while
   `party_ledger` PAYABLE effects are the ledger
   truth — record and ledger linked by document id,
   same pattern as sales.
3. A goods receipt is a PURCHASE-STATUS TRANSITION
   (RECEIVED already exists) that emits stock
   movements — NOT a separate record. Receiving posts
   stock IN movements against the purchase order
   lines; short/over delivery is recorded as line
   adjustments on the same record.
4. Availability checks: SAME PORT PATTERN as Phase
   22 — the check lives in `domain/inventory` over an
   injected snapshot; purchasing supplies the
   snapshot.
5. Debit-note pattern: CONFIRM the 21.4 credit-note
   mirror — positive quantities, side-swapped
   positive journal, NEGATIVE PAYABLE effect, stock
   OUT on return to supplier.

## Package 23.1 — Typed payables record

Status: ACCEPTED on `e242f95` (2026-10-25). Trio green:
Native 40172584050, Compose 40172584124, Segmented
40172584048. 3 new files, 2 modified, 4 new tests
(all in `VendorPayableTest`).

Contents: `VendorPayableRecord` over whitelisted
"vendor_payables" — one record per purchase, the id
IS the purchase document id (the same
one-record-per-document shape as SalesDocument in
"orders"), so a status transition re-saves the same
id and the payable for a purchase is a direct
lookup. Status stored as text (a PurchaseStatus
name); total/paid amounts as BigDecimals with
currency on the row; `balanceDue` derived (total
minus paid), mirroring PurchaseInvoice.balanceDue.
`paidAmount` is the CACHED derived value per
supervisor decision 1 — the derivation from payment
records is the authority in later packages, and this
field is recomputed on payment/credit events.
`VendorPayableJson` is the tolerant codec (same
contract as 22.3: unknown/missing fields default,
unparseable payloads decode to a minimal record
carrying only the id, unparseable amounts decode as
ZERO, amounts as plain strings). `PurchasesViewModel`
gains `savePayable` / `loadPayables` /
`loadPayableForPurchase`; feature/purchases gains
the kotlinx-serialization-json 1.6.2 dependency
(same version as feature/sales and feature/accounts).
The table is mutable (NOT INSERT_ONLY), so
re-saving the same id overwrites — status changes
do not append corrections.

Design: the typed `vendor_payables` record and its
tolerant codec, mirroring the 22.3 PartyLedgerRecord
shape (record + Json over the whitelisted table,
status-bearing, linked to the purchase document by
id). This is the foundation every later 23 package
builds on: receiving transitions, payment
application, and debit notes all write through it.

## Package 23.2 — Phase gate

Status: NOT STARTED.
