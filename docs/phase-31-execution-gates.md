# Phase 31 execution gates — Field Operations Pack

Branch `phase-31-field-operations` from post-gate hotfix `8234472`.
Prep: `agent_notes/phase-31-prep.md` (goal workspace).
Each package is accepted only with a green trio on its exact head.
PR stays draft; no merge without the owner's approval.

## Package 31.1 — Delivery on orders

Sales-order editor gains a Delivery section for CONFIRMED and beyond:
per-line delivered/ordered counts, a quantity field and a Deliver button
wired to `SalesViewModel.recordDelivery` (21.2), so the order status
re-derives to PARTIALLY_FULFILLED / FULFILLED. Recording a delivery
posts no stock movement — movements post at invoice time, never twice.
Tests: JVM orchestration (partial then full delivery moves status) and
an emulator test driving the new controls. Head `5b1454d`.

## Package 31.2 — Receive/pay money

Sales receive: `SalesViewModel.applyReceipt` allocates a receipt across
the party's SENT / PARTIALLY_PAID / OVERDUE invoices, oldest due first,
with already-paid derived from that invoice's PAYMENT_RECEIVED timeline
entries (the 21.1 model — timeline entries are the payment rows). The
posting rides `SalesPaymentPosting` (Dr Cash, Cr Receivable, RECEIVABLE
−amount) through the shell handler; the editor's Receive section takes
an amount plus cash and receivable account pickers, and any unallocated
remainder is reported, never silently dropped. Supplier pay mirrors it:
`PurchasePaymentPosting` (Dr Payable, Cr Cash, PAYABLE −amount), a
payables section in the Purchases screen with a pay form, and
`PurchasesViewModel.applyPayment` clamping at the total
(PARTIALLY_PAID → PAID). The Payment record itself is NOT persisted —
journal + document status + timeline entries are the record. First head
`acee5fc` went all red on a staging miss (PaymentId/PaymentMethodId
imported from domain.payment; they live in domain.common — the build
failed at :feature:purchases:compileDebugKotlin before any test ran);
fix `7a8a18f` is import moves only, 4 files. Tests: 4 new — receipt
allocation JVM, purchase posting JVM, payable applyPayment JVM, sales
receive emulator. Gap: no purchases compose test (JVM coverage only).

## Package 31.3 — Cash in/out

A CASH mode inside AccountsScreen (no new registry module — the
accounts feature owns posting, so no shell handler is needed):
cash-account picker + counterpart picker over active accounts
(AccountType has no CASH value, so no type filter — the user
picks which account is cash), IN/OUT toggle, amount, description,
and a Record button posting straight through
`AccountsViewModel.postDocument`. `CashPosting` builds the
journal (IN = Dr cash / Cr counterpart, OUT mirrored,
documentType "CASH", one synthetic line, no party effect — the
general book, not a second receipt path; customer/supplier cash
already flows through 31.2). `CashBook` reads the selected
account's entries (date-ordered) and balance (debit minus credit
on that account's lines) from the observed journal entries, so a
just-recorded entry appears through the same Flow. Currency
stays the shell "XXX" default. First head `43de9cb` went all red
on a staging miss (DocumentPostResult is nested inside
AccountsViewModel, so the screen must qualify it — the build
failed at :feature:accounts:compileDebugKotlin before any test
ran); fix `74d533c` is one line. Tests: 5 new — CashPosting IN/OUT,
CashBook filter/order + balance, cash-book emulator flow.

## Package 31.4 — Pending orders view

A status filter row (All / Pending / Fulfilled / Cancelled)
above the sales list, feeding the existing LazyColumn
unchanged — no row changes, so existing row-text tests keep
passing. The filter is the pure `SalesDocumentFilters`
function, order-scoped by design (the walkthrough asked about
pending ORDERS): PENDING = sales orders in DRAFT / CONFIRMED /
PARTIALLY_FULFILLED, FULFILLED = sales orders FULFILLED only,
CANCELLED = any document type CANCELLED, ALL = previous
behaviour. Quotations and invoices are never "pending orders",
and a PAID invoice is not a fulfilled order. Accepted first
try on `4a46310`, no fix needed. Tests: 5 new — one JVM test
per filter branch plus a mixed list, and an emulator test
tapping Pending then Fulfilled over seeded orders.

## Package 31.5 — Overdue alerts

## Package 31.6 — Gate

31.1 ACCEPTED at `5b1454d`: Native 40563706397 / Compose 40563706406 / Segmented 40563706375, all success.

Device fix ACCEPTED at `ef32f19`: Native 40567265119 / Compose 40567265106 / Segmented 40567265098, all success. The workflow now preserves the plain full-modules APK as acceptance-evidence/app-debug-full-modules.apk (and the ABC Pharmacy demo build separately), so a delivered APK is never the inventory-only demo again. DAO row-touch (touchRole/touchMembership inside the edit/assign transactions) makes role-permission edits and member role assignments re-emit, so the Access screen refreshes on save instead of after restart. Umar confirmed on device that permissions appear after restart, proving the save worked all along and only the list was stale.

31.2 ACCEPTED at `7a8a18f`: Native 40574224417 / Compose 40574224443 / Segmented 40574224430, all success. First head `acee5fc` went all red on the wrong import package (no test ran); the fix is import moves only.

31.3 ACCEPTED at `74d533c`: Native 40581568347 / Compose 40581568321 / Segmented 40581568362, all success. First head `43de9cb` went all red on the unqualified nested result type (no test ran); the fix is one line.

31.4 ACCEPTED at `4a46310`: Native 40587885090 / Compose 40587885227 / Segmented 40587884993, all success. First-try green, no fix needed.
