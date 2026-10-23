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

Status: NOT STARTED.

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
