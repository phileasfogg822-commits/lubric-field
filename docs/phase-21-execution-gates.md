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

Status: ACCEPTED on `fdd3086`.

Convert-to-Invoice: `SalesDocumentConversion.salesOrderToInvoice`
returns an `InvoiceResult` — only CONFIRMED, PARTIALLY_FULFILLED,
and FULFILLED orders convert (DRAFT is not a commitment,
CANCELLED is dead); an order with `convertedToId` set never
converts twice (single-link semantics); the invoice is an
unnumbered DRAFT with party, dates, notes, and lines copied
including tax snapshots, `fulfilledQuantity` zeroed (invoice
delivery is its own history); the order keeps its status and
gains the forward link.

Delivery recording: `SalesDocumentStatus.recordDelivery`
returns a `DeliveryResult` — positive quantities only
(returns are 21.4), CONFIRMED+ orders only, unknown line
throws, status re-derived via `applyFulfilment`. It posts no
stock movement: movements post at invoice time in 21.3,
never twice.

Section 213 reservations: `StockReservationRecord` (id,
orderId, lineId, itemId, warehouseId?, batchId?, quantity,
status, createdAt) with `ReservationStatus` ACTIVE/RELEASED
and a caller-supplied `ReservationPolicy` ON_SUBMIT/ON_CONFIRM/
NEVER, over whitelisted "reservations"; pure
`activeReservedQuantity` + `availableQuantity` helpers;
ViewModel `observeReservations`/`loadReservations`/
`loadReservationsForOrder`/`saveReservation`/`reserveForOrder`
(NEVER reserves nothing and returns current active, skips
itemless lines, never double-reserves)/`releaseForOrder`.

Verification (GitHub Actions on exact head `fdd3086`,
PR #30): Native Android CI run 40085872528 — completed
success; Compose UI Emulator Tests run 40085872444 —
completed success; Phase 12 Segmented Verification run
40085872535 — completed success. 21.2 ACCEPTED.

Tests: 12 new (conversion 2, fulfilment 4, reservation 3,
orchestration 3 — gate recount; the package-time claim of 11
was an arithmetic slip, 2+4+3+3 = 12).

## Package 21.3 — postDocument wiring + serial SOLD flip

Status: ACCEPTED on `183f0ee` (fix head; package head `ed1382a`
failed all three workflows on a compile error, see below).

Invoice posting builder: `SalesInvoicePosting.invoiceToPosting`
turns an invoice (header + lines) into a `DocumentPosting`
for the Phase 20 engine — totals from `SalesDocumentEngine`
so validator reconciliation is automatic; journal Dr
Receivable = grandTotal, Cr Revenue = grandTotal − taxTotal,
Cr Tax = taxTotal only with a tax account (without one the
tax folds into revenue, journal still balances); zero-total
invoices post an empty journal; one OUT stock movement per
stocked positive line; transaction id = invoice id, so
re-posting overwrites instead of double-posting. Refuses
non-invoices, empty lines, and negative lines (returns are
21.4). The app-layer join (screens → postDocument +
markSerialsSold) lands in 21.5: feature modules do not
depend on each other.

Serial numbers on lines: `SalesDocumentLine.serialNumbers`
(default empty, additive) with a tolerant codec (missing /
non-array → empty, blanks dropped). Sale-time SOLD flip:
pure `flipSerialsToSold` matches (itemId, serialNumber)
IN_STOCK records, one per distinct number, and reports the
rest as not-flipped; `InventoryViewModel.markSerialsSold`
persists only flipped records through `saveSerial`, the
way `removeSerial` writes off.

Verification (GitHub Actions on exact head `183f0ee`,
PR #30): Native Android CI run 40087838685 — completed
success; Compose UI Emulator Tests run 40087838622 —
completed success; Phase 12 Segmented Verification run
40087838569 — completed success. 21.3 ACCEPTED.

Failure history: package head `ed1382a` failed all three
(Native 40087058432, Compose 40087058423, Segmented
40087058439) on one compile error —
`SalesDocumentLineJson.kt:50`, bare `add(it)` for a String
inside `buildJsonArray` does not resolve in this setup.
Fix at `183f0ee`: `add(JsonPrimitive(it))`, the pattern
`ItemJson` already uses. Lesson: string elements into a
`buildJsonArray` always go through `JsonPrimitive`.

Tests: 8 new (posting 5, line serialNumbers codec 1,
serial flip 2 — gate recount).

## Package 21.4 — Returns + credit notes

Status: ACCEPTED on `71e9e4c` (fix head; package head `7efb6bd`
failed all three workflows on a compile error, see below).

Record keys, same reuse as invoices: new `SalesDocumentType`
values SALES_RETURN (prefix SR) and CREDIT_NOTE (prefix CN)
on whitelisted "orders"/"order_items" — codec unchanged
(enum-name serialization), numbering branches added to the
exhaustive `DocumentNumberDefaults.recordFor`, type-filtered
ViewModel surfaces `observeReturns`/`loadReturns`/
`observeCreditNotes`/`loadCreditNotes` mirroring invoices.

Return posting builder: `SalesReturnPosting.returnToPosting`
(SALES_RETURN) and `creditNoteToPosting` (CREDIT_NOTE)
share one core. The journal swaps sides with positive
amounts instead of negating invoice lines: Dr Revenue =
grandTotal − taxTotal, Dr Tax = taxTotal (only with a tax
account; without one the tax folds into revenue), Cr
Receivable = grandTotal; zero-total documents post an
empty journal. A return restores stock with IN movements;
a credit note is financial only and posts none. The party
effect is RECEIVABLE with a negative amount (the balance
falls); nothing persists party effects yet — 20.2's
postDocument does not write party_ledger rows — so the
negative amount is safe until Phase 22's ledger. Refund
settlement (§64 Refund Mode) is Phase 22's payments work:
21.4 records the documents and postings, not the refund.

Invoice → return conversion: `invoiceToReturn` converts
only SENT, PAID, PARTIALLY_PAID, or OVERDUE invoices (DRAFT
sold nothing, CANCELLED is dead) into an unnumbered DRAFT
return copying party, dates, notes, and lines. Deliberately
unlike 21.2, the invoice gains no forward link: one invoice
takes many partial returns, so no single link can name
them. Serial flip back: pure `flipSerialsToStock`
(SOLD → IN_STOCK, per item, one per distinct number) and
`InventoryViewModel.markSerialsReturned` persisting only
flipped records; a damaged return goes through the
existing write-off, not here.

Verification (GitHub Actions on exact head `71e9e4c`,
PR #30): Native Android CI run 40090823906 — completed
success; Compose UI Emulator Tests run 40090823897 —
completed success; Phase 12 Segmented Verification run
40090823894 — completed success. 21.4 ACCEPTED.

Failure history: package head `7efb6bd` failed all three
(Native 40090474410, Compose 40090474389, Segmented
40090474388) on one compile error — `invoiceToReturn`
uses `SalesStatus` without importing it. Fix at `71e9e4c`:
the import. Lesson, second in a row: staging misses are
type-resolution misses, which the local static checks
cannot catch — CI is the compiler here.

Tests: 9 new (posting 4, conversion 2, type filters 1,
serial flip 2 — gate recount; the SR/CN prefix assertions
extend an existing numbering test rather than adding one).

## Package 21.5 — Screens

Status: ACCEPTED on `c596370` (trio green: Native
40106249885, Compose 40106249903, Segmented 40106249911).
9 files, +911/−26 vs `36b7cb8`. 9 new tests (VM
orchestration 1, Compose 4, app handler 4).

Contents: the sales screen now shows invoice, return, and
credit-note type labels in the list and the editor (they
previously all read as "Sales order"); order → invoice and
invoice → return conversions in the editor, gated on the
21.2/21.4 statuses; status buttons scoped to quotations and
sales orders only (invoices, returns, and credit notes get
none — their lifecycle moves through posting and payments,
not order transitions); and the app-layer posting join.
`SalesViewModel.convertInvoiceToReturn` orchestrates the
21.4 conversion (no forward link on the invoice). The
shell wires the screen's posting callback to
`SalesPostingHandler`, the one place that sees all three
ViewModels: build the posting with the 21.3/21.4 builders,
post through `AccountsViewModel.postDocument`, then only
on Posted persist the posting's stock movements (SALE
outbound negative, RETURN inbound positive, keyed by the
document as referenceId; skipped when the document already
has rows, so a retry cannot double-count) and flip serials
(SOLD for invoices, back to stock for returns). The screen
asks the user to pick the posting accounts (receivable +
revenue required, tax optional — no invented mapping);
posting does not change the document's status, and a
POSTED timeline entry is written on success.

Failure history: package head `9f0a276` failed all three
on two compile errors — the handler shared one if/else
value over the unrelated `FlipSoldResult`/`FlipStockResult`
types (resolving to Any, so `.flipped` did not compile)
and the orchestration test used `assertNull` without
importing it. Fix `0ddf658` cleared both; its trio went
2/3 (Native + Segmented green, Compose red on 3 new
Compose tests). The editor's actions lived in LazyColumn
items, which never compose below-fold nodes — the 19.4
lesson, relearned: fix `59661e5` made the editor a
vertically scrolling Column and added waits for the
editor to open. That trio went 2/3 (Compose red on the
post-picker test only): the picker rows were a bare
radio dot beside a name, so taps on the name silently
did nothing, the picks never registered, Confirm stayed
disabled, and the Posted notice never appeared. Fix
`c596370` made every picker row a selectable row (role
RadioButton) with a display-only dot. Known
inconsistency left for a later pass: the party picker
rows still use the bare dot-plus-name pattern.

Deferred: availability-aware stock checks and refund
settlement stay with Phase 22; currency wiring through
AppModuleRegistry is still shell work.

## Package 21.6 — Phase gate

Status: NOT STARTED.
