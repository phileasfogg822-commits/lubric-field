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

## Package 16.1 — units + conversions — ACCEPTED

What was wired:

- **New record types in the operational store**: `ItemUnit` (id,
  name) as "units" and `UnitConversion` (id, fromUnitId, toUnitId,
  factor as `BigDecimal`) as "unit_conversions", each with a
  tolerant codec on the established pattern (`ItemUnitJson` /
  `UnitConversionJson`). The entity is named ItemUnit so it never
  shadows `kotlin.Unit` in this package. §25's names (Piece /
  Bottle / Liter / Carton / Box / Dozen / Kg / Meter) ship as
  `SUGGESTED_UNIT_NAMES` quick picks in the form — a convenience,
  not an enum.
- **Conversion factor semantics**: the factor states how many
  to-units make one from-unit (1 Carton = 12 Bottles is
  Carton → Bottle, factor 12). A missing/unreadable stored factor
  decodes as a zero sentinel; the rule below never converts with
  a non-positive factor, and the form only saves factors > 0.
- **New** `UnitConversionMath`: the pure one-hop rule — same unit
  returns the amount; a direct record multiplies; the same record
  read backwards divides (10 decimal places, HALF_UP, trailing
  zeros stripped); anything else returns null. No chaining
  through a third unit, ever — a missing path is shown as "no
  conversion", never guessed.
- **`Item` gains `unitId`** beside the legacy free-text `unit`;
  the codec round-trips it and pre-16.1 payloads decode with a
  null unitId. `ItemFormState` carries it; the item form gains a
  unit picker (the free-text field stays for old records), and
  the list shows "Unit: X" — the resolved unit name, else the
  free-text name, else "—" for a dangling link.
- **ViewModel surface**: observe/load/save/delete for both
  tables through the same tombstone delete path; **new**
  `ItemUnitsMode` (ItemUnits.kt) manages units and conversions
  with item counts, from/to pickers, and orphan-worded delete
  dialogs, reached from a Units button on the items screen.

Tests:

- **New** `ItemUnitsJsonTest` (6): unit + conversion round-trips;
  factor as JSON number and with precision; bad/missing factor →
  zero sentinel; `Item.unitId` round-trips and legacy payloads
  decode without it.
- **New** `UnitConversionMathTest` (7): same-unit, direct,
  inverse exact, inverse precision, missing path → null,
  non-positive factor → null both directions, no chaining.
- **New** `ItemUnitsFormStatesTest` (6): name trim; valid build;
  same-unit, unpicked-side and bad-factor rejections; load drops
  the zero sentinel and blank ids.
- **New** `ItemsViewModelUnitsTest` (3): CRUD on the "units" and
  "unit_conversions" tables through the codecs; legacy payload.
- **Compose** +2: unit lifecycle (create from a suggested name,
  assign via the picker, count, delete → "Unit: —") and
  conversion lifecycle (two units, Carton → Bottle ×12 saved
  through the codec and shown in the units mode).

Local evidence: escaped-quote grep = 0, braces balanced, strings
cross-checked against every R.string reference, and strings.xml
parses on the staged package (re-verified after integration).

Verification: PR #25 (opened 2026-10-23) fired the trio on the
exact head `d3c3033` — Native Android CI 39950141032, Compose UI
Emulator Tests 39950141011, Phase 12 Segmented Verification
39950140979 — all completed success. Package 16.1 ACCEPTED on
`d3c3033`.

## Package 16.2 — branches + warehouses — ACCEPTED

What was wired:

- **New record types in the operational store**: `Branch` (id,
  name) as "branches" and `Warehouse` (id, name, branchId) as
  "warehouses", each with a tolerant codec on the established
  pattern (`BranchJson` / `WarehouseJson`). §34's Main Shop /
  Godown / branch stores are all just warehouses under this
  shape.
- **Host decision**: branches and warehouses live in
  feature/items as a Locations mode, on the same store, ViewModel
  and playbook as the rest of Phase 16 master data.
  feature/settings is preferences-only, feature/inventory's typed
  surface is Phase 19, and a new Gradle module plus navigation
  wiring is shell work beyond this package. Recorded in the
  Phase 16 prep note.
- **Orphan policy (standing)**: a warehouse keeps its branchId
  when the branch is deleted; the link renders "—" and the delete
  dialog says exactly that, because sync may resurrect the
  branch.
- **ViewModel surface**: observe/load/save/delete for both
  tables through the same tombstone delete path; **new**
  `ItemLocationsMode` (ItemLocations.kt) manages branches (with
  warehouse counts) and warehouses (with branch lines and a
  branch picker) in one LazyColumn. The items screen's entry
  rows are split in two (Add item / Categories / Brands, then
  Units / Locations) so five buttons no longer overflow one row.

Tests:

- **New** `ItemLocationsJsonTest` (4): branch + warehouse
  round-trips; warehouse without branch; legacy payloads; bad
  payloads decode minimal.
- **New** `ItemLocationsFormStatesTest` (3): trims; warehouse
  carries its branch pick; blank loads drop.
- **New** `ItemsViewModelLocationsTest` (3): CRUD on the
  "branches" and "warehouses" tables through the codecs.
- **Compose** +1: branch and warehouse lifecycle — create,
  assign, counts, delete the branch → the warehouse keeps its
  link and shows "Branch: —".

Local evidence: escaped-quote grep = 0, braces balanced, strings
cross-checked against every R.string reference, and strings.xml
parses on the staged package (re-verified after integration).

Verification: PR #25 fired the trio on the exact head `59a2ed4` —
Native Android CI 39952477719, Compose UI Emulator Tests
39952477924, Phase 12 Segmented Verification 39952477906 — all
completed success. Package 16.2 ACCEPTED on `59a2ed4`.

## Package 16.3 — price lists — ACCEPTED

What was wired:

- **New record types in the operational store**: `PriceList`
  (id, name) as "price_lists" and `PriceListItem` (id,
  priceListId, itemId, rate as nullable `BigDecimal`) as
  "price_list_items", each with a tolerant codec on the
  established pattern. The table names are the ones the Phase 12
  sync store already whitelists — the prep draft's
  "price_list_rates" was corrected to the whitelist's name.
- **Rate semantics**: a stored rate that is missing or
  unreadable decodes as null — data damage, not a price. The
  entry form only saves a parsed rate of zero or more (zero is a
  legitimate promotional rate).
- **New** `PriceListResolution`: the pure rule §26 implies —
  the list's own rate for the item wins; without a usable entry
  the item's own sale rate applies; with neither, null. Nothing
  consumes it yet: documents and invoices (Phases 18/21) will
  resolve through here instead of re-deriving precedence.
- **Standing boundaries**: party and customer-group `priceLevel`
  strings stay free text in this phase — binding them to
  PriceList entities is a later phase's decision, not silently
  done here. §27 party-wise price memory still needs sales
  history (Phases 18/21).
- **ViewModel surface**: observe/load/save/delete for both
  tables through the same tombstone delete path; deleting a
  list never edits its entries — they keep their list id, show
  in no list, and stop resolving; the delete dialog says so.
  **New** `ItemPriceListsMode` (ItemPriceLists.kt): lists with
  entry counts, one list's entries (item name + rate), both
  forms with an item picker. A third entry button (Price lists)
  joins the items screen's second button row.

Tests:

- **New** `PriceListsJsonTest` (5): list + entry round-trips;
  rate as JSON number; missing/bad rate → null; legacy/bad
  payloads decode minimal.
- **New** `PriceListResolutionTest` (5): list rate wins; sale
  rate fallback; null when neither; damaged (null-rate) entry
  ignored; another item's entry never leaks.
- **New** `ItemPriceListsFormStatesTest` (5): name trim; valid
  entry build; zero rate allowed; unpicked item / blank /
  non-numeric / negative rates rejected; load drops blank item
  and null rate.
- **New** `ItemsViewModelPriceListsTest` (2): CRUD on both
  tables through the codecs.
- **Compose** +1: price-list lifecycle — item, list, entry saved
  through the codec, and the stored entry is exactly what the
  resolution rule consumes (asserted inside the test).

Local evidence: escaped-quote grep = 0, braces balanced, strings
cross-checked against every R.string reference, and strings.xml
parses on the staged package (re-verified after integration).

Verification: PR #25 fired the trio on the exact head `1c71234` —
Native Android CI 39954722627, Compose UI Emulator Tests
39954722622, Phase 12 Segmented Verification 39954722664 — all
completed success. Package 16.3 ACCEPTED on `1c71234`.

## Package 16.4 — batches — PENDING CI

What was wired:

- **New record type in the operational store**: `Batch` (id,
  itemId, batchNumber, manufacturing/expiry dates as ISO text,
  purchase/sale rates + MRP + quantity as nullable `BigDecimal`s)
  as "batches", with a tolerant codec on the established pattern
  (`BatchJson`). Batch tracking is optional per item (§29): an
  item has no batch records until someone adds one.
- **Quantities are stored data only — the standing boundary of
  this package.** A batch quantity recorded here never moves,
  sums, or drives availability; the stock ledger is Phase 19.
  The delete dialog states this explicitly so the data can never
  be mistaken for counted stock. Dates are stored exactly as
  entered (yyyy-MM-dd by convention) — no calendar math in this
  phase.
- **ViewModel surface**: observe/load/save/delete for "batches"
  through the same tombstone delete path; deleting an item
  never edits its batches — their item ids stay and render "—".
  **New** `ItemBatchesMode` (ItemBatches.kt): every batch
  across items with item-name, expiry and quantity lines, and
  the batch form (item picker + all §29 fields). A Batches
  button joins the items screen's second entry row.

Tests:

- **New** `BatchJsonTest` (3): full §29 round-trip; numbers as
  JSON numbers with precision kept; legacy/bad payloads decode
  minimal.
- **New** `BatchFormStateTest` (4): trims text fields; rejects
  unpicked item, blank batch number and non-numeric amounts;
  load round-trip.
- **New** `ItemsViewModelBatchesTest` (2): CRUD on the "batches"
  table through the codec; legacy payload decode.
- **Compose** +1: batch lifecycle — item, then a batch saved
  through the codec with expiry and quantity shown in the list
  and asserted as stored data.

Local evidence: escaped-quote grep = 0, braces balanced, strings
cross-checked against every R.string reference, and strings.xml
parses on the staged package (re-verified after integration).
First verification is CI on this head.
