# Phase 15 — Item / Service / Media Catalog (Roadmap V2)

Branch: `phase-15-item-catalog` (from the Phase 14 candidate,
`phase-14-party-master-data` at `27e714a`). Draft PR to follow via
the connector. Packages are accepted only with green-CI proof on
the exact SHA, as in Phases 12–14.

## Authoritative scope

Master spec §20 (item system: Item ID, Name, SKU, Barcode, Category,
Brand, Unit, Purchase Rate, Sale Rate, Wholesale Rate, MRP, Tax,
Stock, Image, Description), §21 (types: Product / Service / Combo /
Bundle / Manufactured Item / Expense Item), §22 (product images),
§23 (categories: hierarchy), §24 (brands as a separate entity),
§25 (units — entity and conversions belong to Phase 16; Phase 15
stores a free-text unit name), §26 (multiple prices: Purchase /
Retail / Wholesale / Dealer / Minimum / MRP as data), §28 (barcode
string stored only; scanning is Phase 16).

Items live in the Phase 12 operational store as "products" records —
offline CRUD, sync and tombstones already exist; Phase 15 layers the
typed model and UI over that store and does not replace it, and does
not change delete semantics (products are nonfinancial master data,
soft-deleted by tombstone per the Phase 12 sync contract).

Standing limitations: no inventory ledger exists until Phase 19, so
Phase 15 stores an opening stock quantity as data and never invents
computed stock. The tax engine is Phase 17 — Phase 15 stores a tax
percent only. §27 party-wise price memory needs sales history
(Phases 18/21) and is out of scope here, by decision.

## Package split

- **15.1** Typed Item model + codec + typed ViewModel surface.
- **15.2** Item form UI (full §20 field set, §21 type selector) and
  list facts that exist today.
- **15.3** Categories (own record type, parentId hierarchy) + Brands
  (own record type), typed models + CRUD + item-form pickers;
  orphan policy = the 14.3 rule (keep dangling id, UI shows "—").
- **15.4** Media: image references on the item (main + additional
  URI strings in the payload; no binary in operational records),
  editor in the form, main image shown where the platform renders
  it. Thumbnail generation only if honestly shippable — never faked.
- **15.5** Phase gate: evidence sweep + close-out + Phase 16 prep.

## Package 15.1 — typed Item model + codec — PENDING CI

What was wired:

- **New** `Item` (feature-level, §20/§21/§26): name, type, SKU,
  barcode string, categoryId, brandId, free-text unit name, the six
  §26 rates (purchase / sale / wholesale / dealer / minimum / MRP)
  and tax percent as `BigDecimal?` in base currency, opening stock
  quantity as data, description. **New** `ItemType` enum in the
  feature package (PRODUCT / SERVICE / COMBO / BUNDLE /
  MANUFACTURED / EXPENSE) — the domain Item scaffold defines no
  type enum, so the §21 vocabulary lives with the feature model,
  mirroring how parties reuse the domain `PartyType`.
- **New** `ItemJson` codec on the PartyJson pattern (manual
  `JsonObject` over kotlinx-serialization-json 1.6.2, no compiler
  plugin, no org.json): amounts as plain strings so BigDecimal
  precision survives; tolerant decode — record id beats in-payload
  "uuid", numbers accepted as JSON numbers or strings, unknown type
  falls back to PRODUCT, unparseable payloads decode minimal.
  Dependency added to `feature/items` (same version as parties).
- **Typed ViewModel surface** on `ItemsViewModel`:
  `observeItems` / `loadItems` / `saveItem` through the codec. The
  raw record methods stay as the low-level passthrough (pinned by
  the existing `ItemsViewModelTest`); the screen moves to the typed
  surface in 15.2.
- Deliberate absences: image references are 15.4; the Unit entity
  and conversions are Phase 16 (unit is a free-text name here);
  category/brand are bare ids until their entities land in 15.3.

Tests:

- **New** `ItemJsonTest` (8): full-field round-trip; legacy
  name-only payload decodes minimal; record id beats payload uuid;
  numbers as strings and as JSON numbers; unparseable and blank
  payloads decode minimal; every §21 type decodes, unknown falls
  back to PRODUCT, lowercase tolerated; BigDecimal precision
  (0.10 scale, large amounts) survives; explicit nulls and bad
  numbers tolerated.
- **New** `ItemsViewModelTypedTest` (3): legacy + typed records
  decode through loadItems; observeItems maps through the codec;
  saveItem encodes through the codec onto the "products" table.

Local evidence: escaped-quote grep = 0 and braces balanced on
every changed Kotlin file. First verification is CI, as before.
