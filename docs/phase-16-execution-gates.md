# Phase 16 — Branches / Warehouses / Pricing / Batch / Barcode (Roadmap V2)

Branch: `phase-16-branches-pricing-batch` (from the Phase 15
candidate, `phase-15-item-catalog` at `134041a`). Draft PR to
follow via the connector. Packages are accepted only with
green-CI proof on the exact SHA, as in Phases 12–15.

## Authoritative scope

Master spec §25 (units: Piece / Bottle / Liter / Carton / Box /
Dozen / Kg / Meter, and conversions — 1 Carton = 12 Bottles;
purchases in cartons stock as bottles), §26 (multiple prices: the
six item rates landed as data in 15.1; this phase adds
price-list-specific rates), §28 (barcode support: EAN / UPC /
Code 128 / QR / internal barcodes; the functions are scan /
search / add / invoice / stock count / receiving), §29 (batch
tracking, optional per item: Batch Number, Manufacturing Date,
Expiry Date, Purchase Price, Sale Price, MRP, Quantity), §34
(warehouses: Main Shop / Godown / branch stores; transactions may
be warehouse-specific).

All new master data lives in the Phase 12 operational store as
typed record payloads — own record types, tolerant codecs,
tombstone deletes, and the standing 14.3 orphan policy (deleting
a linked-to record never edits its referrers; the link stays and
renders "—"), exactly as in Phases 14–15. Delete semantics do not
change.

Standing limitations, stated plainly: the §35 stock-transfer
workflow (Draft / Dispatched / Received / Cancelled) moves stock,
so it belongs to the Phase 19 ledger — this phase stores only the
branch/warehouse master data it will use. No transaction wiring:
warehouse-specific transactions belong to the phases that own
transactions. Batch quantities are stored data only and never
move in Phase 16. §27 customer-specific rates still need sales
history (Phases 18/21) and stay out; party/group `priceLevel`
strings stay text — binding them to PriceList entities is a later
phase's decision, not silently done here. §30 serial number
tracking is NOT in the Phase 16 roadmap title and is out of this
phase by decision (likely Phase 19 stock ops).

## Package split

- **16.1** Units + conversions: Unit entity + Conversion entity,
  pure conversion rule, Item gains `unitId` beside the legacy
  free-text `unit`.
- **16.2** Branches + Warehouses: Branch and Warehouse as own
  record types + CRUD modes.
- **16.3** Price lists: PriceList + rate entries + pure
  resolution rule (list rate, else item sale rate, else null).
- **16.4** Batches: Batch entity + per-item batch list UI;
  quantity is stored data only.
- **16.5** Barcode lookup: exact-barcode search over items +
  barcode search field. HID wedge scanners act as keyboards, so
  wedge input already lands in text fields with no code; camera
  scanning needs a scanning library (a new dependency) and is NOT
  added silently — flagged for the owner if it cannot ship
  honestly in this package.
- **16.6** Phase gate: evidence sweep + close-out + Phase 17
  prep.
