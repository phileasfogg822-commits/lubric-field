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

## Package 15.1 — typed Item model + codec — PENDING

(To be filled at implementation.)
