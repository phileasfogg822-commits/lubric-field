# Phase 22 execution gates — Payments / Receivables / Customer Ledger

Branch: `phase-22-payments-receivables` (cut from Phase 21 head
`8f42a8c`). Draft PR (base `phase-21-sales-fulfilment`).

## Authoritative scope

Phase 22 is the phase that makes invoices collectible. It
consumes what Phase 21 posted and leaves the ledger as its
record. It is the second real consumer of the Phase 20
posting engine (after 21.5's handler): a payment's journal
effect (Dr Cash/Bank, Cr Receivable) goes through
`AccountsViewModel.postDocument` (20.2), not through a
parallel path.

Domain base already exists and must not be rebuilt:
domain/payment `Payment` (id, tenant, business, party,
direction RECEIVED/PAID, methodId, amount, referenceNumber,
relatedInvoiceId, date, notes, metadata) — note its
relatedInvoiceId is a SINGLE invoice, so multi-invoice
allocation needs its own lines; domain/accounting posting
contract + `PartyLedgerEffect` (17.4, RECEIVABLE/PAYABLE,
signed amount); domain/sales `SalesStatus`
DRAFT/SENT/PAID/PARTIALLY_PAID/OVERDUE/CANCELLED; 21.1's
pin (no stored paidAmount — derived from payment rows);
21.4's pin (no sign guards in the posting chain).

Ground facts for the record layer: `party_ledger` is in the
Phase 12 sync whitelist AND is INSERT_ONLY — append-only
by platform rule, like `account_transactions` and
`stock_movements`. No `PartyLedgerRecord` or codec exists
yet anywhere in the tree: Phase 22 builds the record layer
from scratch. 20.2's `postDocument` deliberately does NOT
persist `partyEffect` — "that is Phase 22's job."

Open pins (settle at 22.1, in order): payment record keys
+ whitelist status — there is no `payments` table in the
whitelist, so does a payment live on `cash_movements`
(whitelisted, INSERT_ONLY — the cash-in/out ledger), on
`party_ledger` itself, or on a new local-only table? The
choice decides what syncs. Allocation rules — one payment
across many invoices, one invoice across many payments,
partials, overpayment, and how each moves the invoice's
SalesStatus. Refund settlement mechanics — cash-out vs
credit-against-future-invoices, and which journal sides
each takes. Overdue derivation — from dueDate + remaining,
computed or stored, transition or derived view. Customer
ledger surface — per-party statement (invoices, payments,
returns, credit notes, balance); Phase 24 owns full
reporting, so 22 stays to the ledger itself.

Deferred into 22 from earlier phases: availability-aware
stock checks (21.3/21.4 postings carry movements but do
not check availability) and refund settlement (§64 Refund
Mode — 21.4 posts the return's journal and restores
stock/serials but settles no refund).

## Package 22.1 — Payment allocation core

Status: ACCEPTED on `e2b015c` (2026-10-24). Trio green:
Native 40114613809, Compose 40114613807, Segmented
40114613871. `PaymentAllocation.allocate` in
domain/payment (no new Gradle deps — the module already
depends only on `:domain:common`, so the core is
status-agnostic by construction): only RECEIVED payments
allocate; only same-party invoices; deterministic order by
dueDate then invoiceId (oldest first); outstanding =
grandTotal − alreadyPaid clamped at zero; applied =
min(remaining payment, outstanding); currency mismatch
skips the invoice; leftover reported as unallocated.
11 new tests (PaymentAllocationTest).

Grounded design (2026-10-24, from the live tree):
domain/payment depends ONLY on `:domain:common` — no
`:domain:sales` dep — so the allocation core stays
status-agnostic and needs no Gradle change: it computes
applied amounts and remaining balances, and the caller
maps remaining == 0 → PAID, applied > 0 →
PARTIALLY_PAID. `PaymentAllocation.allocate(payment,
openInvoices)`: only RECEIVED payments allocate (PAID
direction is the payables side, Phase 23); only same-party
invoices; non-collectible (DRAFT/CANCELLED) skipped by
the caller via a collectible flag; deterministic order by
dueDate then invoiceId (oldest first); outstanding =
grandTotal − alreadyPaid clamped at zero; applied =
min(remaining payment, outstanding); unallocated leftover
reported, not decided, here. Money is compareTo-equal,
never raw BigDecimal equals; minus requires same
currency, so a mismatched-currency invoice is skipped,
not forced.

## Package 22.2 — Payment posting + invoice status moves

Status: NOT STARTED.

## Package 22.3 — Party ledger rows from posting effects

Status: NOT STARTED.

Includes the backfill decision for the 21.x postings that
already happened — backfill, or an explicit decision not
to.

## Package 22.4 — Refund settlement

Status: NOT STARTED.

## Package 22.5 — Customer ledger screens

Status: NOT STARTED.

## Package 22.6 — Phase gate

Status: NOT STARTED.
