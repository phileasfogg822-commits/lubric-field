# Phase 21 execution gates — Sales Fulfilment / Invoices / Returns / Credit Notes

Branch: `phase-21-sales-fulfilment` (cut from Phase 20 head
`bd285e5`). Draft PR (base `phase-20-accounting-core`).

## Authoritative scope

Phase 21 is the first REAL consumer of the Phase 20 posting
engine: invoices post through `AccountsViewModel.postDocument`
(20.2), not through a parallel path. It also lands the items
deferred into it: Convert-to-Invoice from Phase 18 orders,
and the sale-time serial SOLD flip from Phase 19. §213 stock
reservations land here or in Phase 22 — decided at branch
time, not split across both.

Domain base already exists and must not be rebuilt:
domain/sales `Sales.kt` (`SalesInvoice`, `SalesInvoiceLine`,
`SalesStatus` DRAFT/SENT/PAID/PARTIALLY_PAID/OVERDUE/
CANCELLED), Phase 17 numbering/totals/posting contract,
Phase 18 `DocumentStatus` fulfilment rules, Phase 19 ledger.

Open pins (resolved at 21.1): invoice record key = REUSE
whitelisted "orders"/"order_items" — an invoice is an
"orders" record with type INVOICE, so invoices sync with no
whitelist edit; returns / credit-notes record keys decided
at 21.4 (same reuse logic applies); fulfilment state
machine vs `SalesStatus` = SPLIT AXES — payment lifecycle
on the header, delivery derived from lines via the §44
rules (21.2); credit-note validator acceptance checked at
21.4.

## Package 21.1 — Invoice model + codec

Status: ACCEPTED on `51ee332`.

Pin resolved: invoices are "orders" records with a new
`SalesDocumentType.INVOICE` value — same whitelisted
table, same lines table, same numbering/conversion-link
machinery. The codec needed no change (enum-name
serialization + the existing unknown-type → SALES_ORDER
fallback). The one compile risk the pin caught: the
numbering defaults use an exhaustive `when` over the
document type, so it gained an INVOICE branch with prefix
"INV" (matching the 17.2 "INV-2026-00001" pattern) —
without it the build would have failed.

ViewModel surface: `observeInvoices` / `loadInvoices`
(type-filtered views over "orders"). Invoice-specific
stored fields: none yet — number, party, dates, notes,
conversion links, and status text already cover the
header; paidAmount/balanceDue derive from Phase 22
payments, not stored here.

Tests (3): invoice codec round-trip, invoice-only
filtering, INV prefix default.

Verification (GitHub Actions on exact head `51ee332`,
PR #30): Native Android CI run 40084450910 — completed
success; Compose UI Emulator Tests run 40084450906 —
completed success; Phase 12 Segmented Verification run
40084450987 — completed success. 21.1 ACCEPTED.

## Package 21.2 — Convert-to-Invoice + fulfilment + reservations

Status: NOT STARTED.

## Package 21.3 — postDocument wiring + serial SOLD flip

Status: NOT STARTED.

## Package 21.4 — Returns + credit notes

Status: NOT STARTED.

## Package 21.5 — Screens

Status: NOT STARTED.

## Package 21.6 — Phase gate

Status: NOT STARTED.
