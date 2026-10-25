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

## Package 23.2 — Purchase invoice posting

Status: ACCEPTED on `6385a46` (2026-10-25). Trio green:
Native 40175728288, Compose 40175728433, Segmented
40175728416. 2 new files, 1 modified build file, 3 new
tests (all in `PurchaseInvoicePostingTest`).

Contents: `PurchaseInvoicePosting` in feature/purchases
— the vendor mirror of 21.3. A purchase invoice and
its lines map through the shared
DocumentTotalsCalculator (line tax from the line's
flat rate via a single-component exclusive scheme) to
a DocumentPosting: Dr Expense / Dr Tax / Cr Payable,
all POSITIVE amounts, with a PAYABLE effect for the
full grand total and NO stock movements (receiving
owns stock per supervisor decision 3). Totals are
recomputed from lines, never trusted from the
invoice's stored totals, so the posting satisfies
PostingValidator by construction; the invoice's
stored total supplies only the currency hint.
Document type "PURCHASE_INVOICE". Posting does not
change the purchase status. feature/purchases gains
the `:domain:accounting` dependency.

## Package 23.3 — Goods receipt

Status: ACCEPTED on `4211378` (2026-10-25). Trio green:
Native 40179782077, Compose 40179782298, Segmented
40179782088. 2 new files, 1 modified build file, 5 new
tests (all in `PurchaseReceivingTest`).

Contents: `PurchaseReceiving.receive` in domain/purchase
— the purchase-status transition (supervisor decision
3). A DRAFT purchase moves to RECEIVED on the same
record, lines adjust to what actually arrived (short
and over delivery both rewrite the line quantity;
absent lines count as zero received), and stock IN
movements emit for positively received lines only.
Only DRAFT purchases receive; a second receipt is
rejected, not merged. Stored money totals are not
recomputed here — totals are recomputed from lines
at posting time (the 23.2 rule). No journal, no
money movement: posting stays 23.2's job.
domain/purchase gains the `:domain:accounting`
dependency (StockMovement in the result type).

## Package 23.4 — Vendor payment application

Status: ACCEPTED on `184b717` (2026-10-25). Trio green:
Native 40180736234, Compose 40180736233, Segmented
40180736276. 2 new files, 2 modified build files, 8 new
tests (all in `PurchasePaymentApplicationTest`).

Contents: `PurchasePaymentApplication.applyPayments`
in domain/purchase — the derivation of a purchase
invoice's paidAmount (supervisor decision 1). The
stored paidAmount is a cache; this function is the
authority, recomputed from the payment set on every
payment event, mirroring 22.2's applyPayment shape
adapted from an event application to a
re-derivation. Only PAID-direction payments linked
to the invoice count; an empty derivation is not an
event; re-derivation is idempotent; otherwise the
invoice must be RECEIVED, PARTIALLY_PAID, or
OVERDUE. Derived >= total lands PAID, else
PARTIALLY_PAID; overpayment lands PAID with a
negative balanceDue, not a clamp. Mixed-currency
sets fail loudly on Money's currency guard. The
credit term (debit notes) lands with 23.5; the
payable record's cache sync belongs to the later
join package. domain/purchase gains the
`:domain:payment` dependency.

## Package 23.5 — Debit notes

Status: ACCEPTED on `5b38031` (2026-10-25). Trio green:
Native 40182269649, Compose 40182269642, Segmented
40182269648. 2 new files, no build-file changes, 5 new
tests (all in `PurchaseDebitNotePostingTest`).

Contents: `PurchaseDebitNotePosting` in
feature/purchases — the vendor mirror of 21.4
(supervisor decision 5). A return to supplier
posts Dr Payable / Cr Expense / Cr Tax with
POSITIVE amounts, a NEGATIVE PAYABLE effect for
the full grand total, and stock OUT movements for
the returned lines. A debit note is its own
document with its own line type; lines carry
positive returned quantities. Totals are
recomputed from lines through the shared
calculator, exactly as in 23.2, so the posting
satisfies PostingValidator by construction. No
bottom-line discount. The credit term for 23.4's
derivation and the payable record's cache sync
belong to the later join package.

## Package 23.6 — Posting join

Status: ACCEPTED on `ca1003c` (2026-10-25). Trio green:
Native 40183908686, Compose 40183908802, Segmented
40183908570. 3 new files, no build-file changes, 4 new
tests (all in `PurchasePostingHandlerTest`).

Contents: `PurchasePostingHandler` in the app shell
— the purchases posting join, the mirror of 21.5.
postInvoice builds with 23.2, posts through
postDocument, and on Posted saves the payable
record (23.1). postDebitNote builds with 23.5,
posts, and on Posted persists the stock OUT
movements as negated PURCHASE rows. Unlike 21.5,
the join takes the invoice and lines as arguments
(no typed purchase-order persistence to load
through); invoice postings move no stock
(receiving owns it); no serial handling; no
creditCheck (PAYABLE effects never trigger it).
Retry-safe by entry id, payable id, and the
already-moved check. The ledger, not the payable
record, is the truth for the debit-note-reduced
balance — no derivation change needed.
