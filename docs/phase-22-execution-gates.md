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

Status: ACCEPTED on `85eb9f8` (2026-10-24). Trio green:
Native 40117230445, Compose 40117230472 (attempt-2
re-run after attempt 1 failed on an unrelated inventory
test — see below), Segmented 40117230449. 5 files,
+334, 11 new tests (builder validator test 1,
status-move tests 10).

Contents: `SalesPaymentPosting.paymentToPosting`
(Dr Cash/Bank, Cr Receivable; one synthetic totals line
— the payment itself — so PostingValidator passes with
no validator change; partyEffect RECEIVABLE negative;
no stock movements; transactionId from the payment id)
and `SalesDocumentStatus.applyPayment` (SENT /
PARTIALLY_PAID / OVERDUE take payments; remaining == 0
→ PAID, else PARTIALLY_PAID; same-state and zero
applications return unchanged). `:domain:payment`
dependency added to feature/sales.

Failure history: attempt 1's Compose run failed on
exactly one test — `InventoryScreenComposeTest.
adjustFlowPostsAMovement` ("Adjustment posted" not
displayed). The 22.2 diff touches no inventory files
(only feature/sales + gates doc) and the same test
passed on the 22.1 trio under an hour earlier, so it
was diagnosed as a timing flake, not a regression;
attempt 2 (failed-jobs re-run only, website route)
went green, and the previously unjudged sales/parties/
purchases/accounts connected tests ran in the re-run.
No code change was made for the flake.

## Package 22.3 — Party ledger rows from posting effects

Status: ACCEPTED on `cf138d2` (2026-10-25). Trio green:
Native 40124055752, Compose 40124055756, Segmented
40124055758. 5 files, +514 (package head `74ed57c`)
plus a test-only fix head `cf138d2` (+9/−1). 11 new
tests (all in `PartyLedgerTest`).

Contents: `PartyLedgerRecord` + tolerant `PartyLedgerJson`
over `party_ledger` (whitelisted, INSERT_ONLY — the
table 20.2 left unwritten); `AccountsViewModel.postDocument`
now persists the posting's party effect in the same save
(completing the job 20.2 left to Phase 22 — a posting
without an effect records nothing, a rejection saves
nothing); row id is the journal entry id
(`je-<transactionId>`), so a re-post overwrites the
same row; `PartyLedgerBalance` net-sums signed
RECEIVABLE / PAYABLE amounts per party per currency,
ignoring unknown direction text, and a negative net
stays a credit balance instead of being clamped.

Backfill decision: YES — backfill party_ledger rows
for the 21.x postings already done, through this same
writer, idempotent by transaction id (prep decision 5).
The trigger / app-layer orchestration that finds the
21.x postings and rebuilds their effects is NOT in
22.3's core; it lands with 22.5's ledger surfaces.

Failure history: package head `74ed57c` went 2/3 green
(Segmented 40123162170, Compose 40123162257) with
Native 40123162319 failing at step 15.3 on exactly 3
tests, all in the new `PartyLedgerTest`, all test bugs
— production code untouched: (1) the test helper built
postings with a party effect but no party id, so the
validator rightly rejected them (PARTY_EFFECT_MISMATCH)
and no ledger row was saved — `postDocumentSavesLedgerRow`
and `repostOverwritesSameLedgerRow` failed; (2) the
`fromPostingMapsEffectAndIds` amount assertion used
raw `BigDecimal.equals`, which is scale-sensitive
against Money's normalized 1E+5. Fix head `cf138d2`
sets the posting party from the effect and uses a
compareTo amount assertion; nothing else changed.

## Package 22.4 — Refund settlement

Status: ACCEPTED on `400d80f` (2026-10-25). Trio green:
Native 40163304053, Compose 40163304051, Segmented
40163304049. 2 files, +199, 3 new tests (all in
`SalesRefundPostingTest`).

Contents: `SalesRefundPosting.refundToPosting` — the
cash-out refund builder mirroring 22.2 with sides
swapped: Dr Receivable / Cr Cash with POSITIVE amounts
(the 21.4 mirror principle); one synthetic totals line —
the refund itself — so PostingValidator passes with no
validator change; partyEffect RECEIVABLE with a POSITIVE
amount (a refund raises a negative credit balance back
toward zero, the opposite sign from a payment's negative
effect); no stock movements; transactionId from the
refund id; documentType "REFUND". Builder requires a
positive amount (zero/negative refunds are data errors,
rejected at build time). No new domain type (reusing
Payment would conflate customer refunds with vendor
payments, Phase 23's payables side); no new dependency;
the typed refund record (method / reference / return
link) lands with 22.5's handler.

Decisions recorded in the builder: (1) post the return
FIRST — the builder takes no balance snapshot, so
over-refunding past zero is the handler's ordering job;
(2) refund callers must NOT supply a validator
creditCheck — the validator fires on any positive
RECEIVABLE effect and cannot distinguish "new credit"
from "clearing a credit balance", while the §18 block
fires only on new positive exposure, which a refund
never creates; (3) credit-against-future-invoices is
DEFERRED to a later package — 22.1 allocation allocates
payments, not credit balances, and extending it is its
own design; until then the credit balance carries
forward per 22.3's rule.

## Package 22.5 — Customer ledger screens

Status: NOT STARTED.

## Package 22.6 — Phase gate

Status: NOT STARTED.
