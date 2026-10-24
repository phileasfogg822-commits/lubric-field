# Phase 18 — Quotations & Sales Orders (Roadmap V2)

Branch: `phase-18-quotations-sales-orders` (from the Phase 17
candidate, `phase-17-transaction-engine` at `96a3cb7`). Draft PR
to follow via the connector. Packages are accepted only with
green-CI proof on the exact SHA, as in Phases 12–17.

## Authoritative scope

Master spec §48 (quotation / estimate: statuses Draft / Sent /
Viewed / Accepted / Rejected / Expired / Converted; actions
Convert to Order, Convert to Invoice), §43 (document conversion:
Quotation → Sales Order → Delivery Challan → Invoice → Payment,
no duplicate data entry), §44 (partial order fulfilment —
ordered vs fulfilled quantities), §14 (transaction timeline),
§51 (automatic numbering — this phase is its first consumer),
and the Phase 17 engine contracts (§8.2 tax, §52 item row,
§53 discounts, §54 charges) which this phase consumes, never
re-derives.

Documents live in the Phase 12 operational store: headers as
"orders" records, lines as "order_items" records — both names
are already in the Phase 12 sync whitelist, so these records
sync, unlike Phase 17's local-only engine tables. Totals are
computed by the 17.3 engine, never stored as truth.

Standing limitations, stated plainly: this phase ends at the
sales order. Invoices (§49/§50) are Phase 21 documents; the
§48 "Convert to Invoice" action records the conversion link
only. Fulfilment here is tracked state (ordered vs fulfilled
quantities per §44); moving stock is Phase 19/21 posting.
Payments against orders are Phase 22. The "numbering_sequences"
counter record is local-only (not sync-whitelisted), so two
devices can still collide on numbers in LOCAL_ONLY mode —
Phase 30 owns cross-device uniqueness.

## Package split

- **18.1** Document model + codecs: sales-document header +
  lines as typed records over "orders" / "order_items".
- **18.2** Statuses + lifecycle: §48 quotation statuses, order
  statuses with §44 fulfilment quantities, pure transition
  rules (the reusable status engine).
- **18.3** Numbering wiring: QT-/SO- sequences via 17.2, the
  "numbering_sequences" state record + codec, number assigned
  on first save.
- **18.4** Quotation / order screens: list + editor over the
  typed surface, totals preview from 17.3, §14 timeline entry
  written on save (the timeline record's first writer).
- **18.5** Conversion: quotation → sales order (§43) — lines
  copied, source marked Converted, links recorded both ways.
- **18.6** Phase gate: evidence sweep + close-out + Phase 19
  prep.

## Package 18.1 — Document model + codecs

Status: ACCEPTED on `de9435d`.

`SalesDocument` (header, an "orders" record: type QUOTATION /
SALES_ORDER, number blank until 18.3, party/date/due dates,
status as text until 18.2, notes, conversion link fields) and
`SalesDocumentLine` (an "order_items" record: position, item,
description, quantity, unit, rate, discount kind + value, the
TAX SNAPSHOT — the scheme's components copied at entry so later
scheme edits never change a document — warehouse/batch refs,
fulfilledQuantity for §44). Both tables are in the Phase 12
sync whitelist. Tolerant codecs in the Phase 14–16 pattern
(record id wins; amounts as exact strings; a damaged tax
component is dropped, never fatal; an unknown header type
decodes as SALES_ORDER, the table's history). Totals are never
stored: `SalesDocumentEngine` is the only bridge from stored
lines to the Phase 17 engine (snapshot → TaxScheme, discount
kind → Discount, totals via DocumentTotalsCalculator; the
currency is the caller's business currency). The ViewModel
gained a typed surface (documents + lines filtered by orderId,
sorted by position) over the untouched raw methods, and
deliberately NO document delete — the repository contract
reserves soft-delete for nonfinancial records; documents
cancel by status (18.2). Gradle: + `:domain:accounting` +
kotlinx-serialization-json 1.6.2.

Tests (10, `feature/sales`): header/line codec round-trips
(incl. the tax snapshot), tolerance (JSON numbers, unknown
type, garbage payload), engine bridge (exclusive totals
1000 → tax 170 → 1170; percent discount before tax; inclusive
extraction + an untaxed line; unknown discount kind ignored),
and the typed ViewModel surface (tables, filter + sort).
Local evidence: staged files brace/paren balanced, zero
escaped quotes; no local JVM/Android SDK, CI is the compiler.

Verification (GitHub Actions on exact head `de9435d`, PR #27):
Native Android CI run 39993392164 — completed success;
Compose UI Emulator Tests run 39993392160 — completed success;
Phase 12 Segmented Verification run 39993392169 — completed
success. 18.1 ACCEPTED.

## Package 18.2 — Statuses + lifecycle

Status: ACCEPTED on `dd0df43`.

The reusable status engine the spec asks for, in domain/sales
(the Sales.kt invoice scaffold is untouched — its SalesStatus
is the Phase 21 invoice lifecycle, a different document):
`QuotationStatus` (the §48 set), `SalesOrderStatus` (DRAFT /
CONFIRMED / PARTIALLY_FULFILLED / FULFILLED / CANCELLED), and
`DocumentStatusRules` — pure transition tables (terminal
states have no exits; same-state is never a transition; a
draft quotation may expire, an accepted one may not be
rejected or expired) plus `fulfilmentStatus(lines)`, the §44
derivation (nothing fulfilled → null and the caller keeps its
status; any partial → PARTIALLY_FULFILLED; all fulfilled ≥
ordered → FULFILLED; over-delivery completes a line). The
feature bridge `SalesDocumentStatus` parses the stored status
text tolerantly (unknown → DRAFT), refuses illegal transitions
and wrong document kinds, and re-derives an order's status
from its lines — refusing to move a cancelled order, which is
a data problem, not a transition.

Tests (11): domain rules (6 — the quotation happy path,
rejection/expiry windows, terminal and skip illegality, the
order lifecycle, cancellation rules, the §44 example
verbatim: HX7 20/20 + Mobil 1 10/4 → PARTIALLY_FULFILLED) and
the feature bridge (5 — legal moves, illegal/wrong-kind
throws, unknown-status tolerance, the fulfilment chain
CONFIRMED → PARTIALLY_FULFILLED → FULFILLED, cancelled-order
refusal). Local evidence: staged files brace/paren balanced,
zero escaped quotes; no local JVM/Android SDK, CI is the
compiler.

Verification (GitHub Actions on exact head `dd0df43`, PR #27):
Native Android CI run 39995658909 — completed success;
Compose UI Emulator Tests run 39995658871 — completed success;
Phase 12 Segmented Verification run 39995659321 — completed
success. 18.2 ACCEPTED.

## Package 18.3 — Numbering wiring

Status: ACCEPTED on `2d796b2`.

The first consumer of the 17.2 contract. `NumberingRecord`
persists one sequence's configuration + counter as a
"numbering_sequences" record whose id is the document-type
key, bridging to `NumberingSequence` / `NumberingState`;
`NumberingRecordJson` is the tolerant codec; the §51 defaults
(QT- / SO-, yearly reset, padding 5, start 1) apply until a
stored record exists. The ViewModel's `issueDocumentNumber`
assigns a number ONCE — a numbered document keeps its number
and the counter does not move — and advances + persists the
counter in the same call. Stated in the code, again:
"numbering_sequences" is NOT in the sync whitelist, so
counters are local-only (Phase 30 owns cross-device
uniqueness). Gradle: + `:domain:business`.

Failure history, recorded honestly: the first head `2a89903`
failed all three workflows — a compile error in the glue
(`DocumentNumbering.issue` returns a `Pair<NumberingState,
String>`; the glue read `.state` / `.formatted` members that
do not exist; log-verified as unresolved references at
SalesViewModel.kt:93–94, no test ever ran). Fixed at
`2d796b2` by destructuring the Pair; the acceptance below is
on the fix head.

Tests (4): codec round-trip + §51 defaults; per-type counters
(QT-2026-00001 / …02, SO-2026-00001, stored state verified);
the already-numbered no-op (no record written); a customised
stored record (EST, padding 3, reset NEVER) driving the
format ("EST-001"). Local evidence: staged files
brace/paren balanced, zero escaped quotes; no local
JVM/Android SDK, CI is the compiler.

Verification (GitHub Actions on exact head `2d796b2`, PR #27):
Native Android CI run 39999373290 — completed success;
Compose UI Emulator Tests run 39999373285 — completed success;
Phase 12 Segmented Verification run 39999373355 — completed
success. 18.3 ACCEPTED.

## Package 18.4 — Quotation / order screens

Status: ACCEPTED on `a118f37`.

The sales surface over the 18.1 typed model, replacing the Phase 12 raw org.json screen entirely. `TimelineEntry` + codec (§14's first writer, "timeline_entries" local-only, not whitelisted); `SalesReferenceData` (tolerant customers/products reads; no dependency on feature/parties or feature/items); `SalesDocumentFormState` + `SalesLineDraft` (text-while-editing numerics, tax snapshot from name + % into one component, `totalsOrNull` via the 18.1 engine); ViewModel `saveDocumentWithLines` (deletes stale lines; header never deleted), `convertQuotationToOrder`, timeline save/load; `SalesScreen` rewrite (one editor, live Grand total, legal 18.2 status buttons, Convert on ACCEPTED, timeline strip); `SalesViewModel.saveForm` (numbering via 18.3, header + lines, SAVED timeline entry). NAMED RESIDUAL: `currencyCode` defaults to "XXX" — wiring BusinessChoice.currency through the registry is shell work, deferred.

Tests (11): SalesDocumentFormStateTest (4), SalesTimelineAndReferenceTest (2), SalesViewModelOrchestrationTest (3), SalesScreenComposeTest (2). The Compose create-quotation test asserts live "Grand total: 1170" and the saved row "QT-2026-00001 · Quotation · DRAFT · 2026-10-23".

Failure history (13 heads): `49aa34a` boundary (hardcoded timeline Text) → `b5a5c14` Money scale (stripTrailingZeros; test compared via equals) → `2be5559` assertIsDisplayed flap → `0c74885` assertExists not importable → `da51b15` waitUntil timeout (save never started) → `ef0ee8f` diagnostic (all tables zero) → `3340b72` println DIAG (logcat not captured) → `0f0e2ee` fetchSemanticsNodes not importable (compile) → `da95ec1` ViewModel.saveForm move → `f8a69b2` inlined onSave → `c3dc871` testTag click → `684a337` lambda probe (proved onSave never ran) → `32ce7f8` moved Save/Cancel out of LazyColumn (stale handler) → `a118f37` dropped performScrollTo (button no longer scrollable). Root causes: LazyColumn item cached a stale onSave; then performScrollTo on a non-scrollable node.

Verification (GitHub Actions on exact head `a118f37`, PR #27): Native Android CI run 40042319091 — 24 steps completed success; Compose UI Emulator Tests run 40042319201 — completed success (13-head red streak broken); Phase 12 Segmented Verification run 40042319153 — completed success.

## Package 18.5 — Conversion

Status: ACCEPTED on `87ff571`.

`SalesDocumentConversion.quotationToSalesOrder` (§43) — pure:
only an ACCEPTED quotation converts (the one legal path into
CONVERTED, enforced by the 18.2 rules). The order is an
unnumbered DRAFT (18.3 numbers it on first save) carrying the
quotation's party, dates and notes and a convertedFromId
link; the quotation is returned marked CONVERTED with
convertedToId set — the links are written both ways. Lines
are copied in position order with fresh caller-supplied ids,
their tax snapshots travel with them, lines belonging to
other documents are ignored, and fulfilledQuantity resets to
zero: fulfilment is the order's own history, never inherited.
Persistence orchestration (saving all three results) lands
with the 18.4 screens, as the converter's contract states.
Built ahead of 18.4 in staging because it is pure logic over
18.1 + 18.2; the integration order stayed
18.1 → 18.2 → 18.3 → 18.5 → 18.4.

Tests (2): the full copy (both-way links, line filtering and
order, snapshot carriage, fulfilment reset) and the guards
(a SENT quotation and a non-quotation both throw). Local
evidence: staged files brace/paren balanced, zero escaped
quotes; no local JVM/Android SDK, CI is the compiler.

Verification (GitHub Actions on exact head `87ff571`, PR #27):
Native Android CI run 40001971169 — completed success;
Compose UI Emulator Tests run 40001971272 — completed success;
Phase 12 Segmented Verification run 40001971171 — completed
success. 18.5 ACCEPTED.

## Package 18.6 — Phase gate

Status: ACCEPTED (gate).

Evidence sweep vs `96a3cb7` (the Phase 17 head this branch was cut from): 29 files, +2,808/−92 (includes the gates doc itself); 38 new tests (18.1: 10, 18.2: 11, 18.3: 4, 18.5: 2, 18.4: 11). All six packages accepted with green-CI proof on exact heads: 18.1 `de9435d`, 18.2 `dd0df43`, 18.3 `2d796b2` (fix head), 18.4 `a118f37` (fix head), 18.5 `87ff571`, 18.6 this gate. Integration order: 18.1 → 18.2 → 18.3 → 18.5 → 18.4 (18.5 is pure logic and was built ahead of the screens).

Deferred items, carried forward explicitly:
- Currency wiring: BusinessChoice.currency through the AppModuleRegistry content signature (shell work) — the sales totals preview defaults to "XXX" until then.
- "timeline_entries" and "numbering_sequences" are local-only (not sync-whitelisted); cross-device number uniqueness is Phase 30.
- Multi-component tax-scheme records have no home yet; the line snapshot model already supports them when one lands.
- "Convert to Invoice" records the link only — invoices (§49/§50) are Phase 21.
- Fulfilment is tracked state (§44); stock moves via the Phase 19/21 ledger postings.

Phase 19 prep: agent_notes/phase-19-prep.md (Inventory Ledger & Stock Ops) is drafted in the goal workspace.
