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

Status: NOT STARTED.

## Package 18.3 — Numbering wiring

Status: NOT STARTED.

## Package 18.4 — Quotation / order screens

Status: NOT STARTED.

## Package 18.5 — Conversion

Status: NOT STARTED.

## Package 18.6 — Phase gate

Status: NOT STARTED.
