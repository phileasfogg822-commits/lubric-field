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

Open pins (resolve before package 21.1): invoice record key
(reuse whitelisted "orders"/"order_items" or a new key —
whitelists are NOT edited inside a feature phase, so a new
key stays local-only unless already whitelisted); returns /
credit-notes record keys + whitelist status; fulfilment
state machine vs `SalesStatus`; credit-note posting through
the 20.2 engine (validator acceptance on negative amounts).

## Package 21.1 — Invoice model + codec

Status: NOT STARTED.

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
