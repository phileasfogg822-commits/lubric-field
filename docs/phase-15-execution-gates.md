# Phase 15 — Item / Service / Media Catalog (Roadmap V2)

Branch: `phase-15-item-catalog` (from the Phase 14 candidate,
`phase-14-party-master-data` at `27e714a`). Draft PR #24 (base
`phase-14-party-master-data`). Packages are accepted only with green-CI proof on
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
  editor in the form. References only — no rendering, no
  thumbnails: the app has no image-loading library and adding one
  is a separate decision; nothing faked.
- **15.5** Phase gate: evidence sweep + close-out + Phase 16 prep.

## Package 15.1 — typed Item model + codec — ACCEPTED

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

Verification (2026-10-23): all three workflows green on the exact
head `61a0636` (pull_request events, PR #24): Native Android CI run
39930991390 — success; Compose UI Emulator Tests run 39930991382 —
success; Phase 12 Segmented Verification run 39930991467 — success.
Package 15.1 ACCEPTED on `61a0636`.

## Package 15.2 — item form UI — ACCEPTED

What was wired:

- **New** `ItemFormState` (text-first, on the party pattern): every
  field edited as text; blank optionals become null; any non-blank
  non-numeric decimal field makes `toItem` return null so the save
  is blocked with a form error, never silently dropped. `categoryId`
  and `brandId` are carried through edits untouched — their pickers
  arrive in 15.3 when the entities exist.
- **`ItemsScreen` rewritten** to list/form modes over the typed
  ViewModel surface only (org.json is gone from the items feature).
  The list shows name, §21 type label, "Sale rate: X" and "Opening
  stock: X" lines when present. The form carries the full §20 field
  set with a type radio list, and deletion keeps the tombstone path
  and the confirm dialog.
- `ItemType.labelRes()` lives in the screen file, as parties did
  for `PartyType`. Strings extended for the items feature
  (`items_save` now reads "Save item"; new title / label / line /
  form-error strings; legacy `items_update` / `items_cancel_edit`
  stay defined but unused, as parties left theirs).

Tests:

- **New** `ItemFormStateTest` (5): round-trip through toItem;
  blank optionals drop to null; a bad decimal in any money field
  blocks the save; type selection survives; category/brand ids
  carried through an edit.
- **Rewritten** `ItemsScreenComposeTest` (8, single-table fake):
  list renders typed facts; save flow writes through the codec;
  edit prefill; delete dialog confirm path; form error on bad
  decimal; type radio selection.

Local evidence: escaped-quote grep = 0, braces balanced, and
strings.xml parses on every changed file (re-checked after the
staging copy). First verification is CI, as before.

Verification (2026-10-23): all three workflows green on the exact
head `f2763ce` (pull_request events, PR #24): Native Android CI run
39932921129 — success; Compose UI Emulator Tests run 39932921298 —
success; Phase 12 Segmented Verification run 39932921111 — success.
Package 15.2 ACCEPTED on `f2763ce`.

## Package 15.3 — categories + brands — ACCEPTED

What was wired:

- **New record types in the operational store**: `ItemCategory`
  (id, name, parentId) as "categories" and `Brand` (id, name) as
  "brands", each with a tolerant codec on the PartyJson pattern
  (`ItemCategoryJson` / `BrandJson`) — record id beats in-payload
  "uuid", blank names decode minimal, no org.json.
- **ViewModel catalog surface** on `ItemsViewModel`:
  observe/load/save/delete for both tables through the same
  tombstone delete path items already use; the raw record methods
  stay untouched.
- **New** `ItemCatalogFormStates.kt`: category form carries
  parentId and guards the self-parent case (`toCategory` drops a
  parentId equal to the category's own id); brand form is
  name-only. Blank name blocks the save for both.
- **New** `ItemCatalog.kt`: categories and brands management
  modes — item counts per category/brand, parent line with the
  "—" fallback, parent picker excludes the category being edited,
  and delete dialogs worded with the standing 14.3 orphan policy
  (deleting never edits referrers; the link stays and renders "—"
  because sync may resurrect the record).
- **`ItemsScreen` wired**: category and brand pickers in the item
  form, "Category: X" / "Brand: X" lines in the list, and a
  Categories / Brands entry row into the management modes.

Tests:

- **New** `ItemCatalogJsonTest` (5) and `ItemCatalogFormStatesTest`
  (5: incl. the self-parent guard) and `ItemsViewModelCatalogTest`
  (3: save/load/delete round-trips per table).
- **Upgraded** `ItemsScreenComposeTest`: per-table fake (a single
  shared flow corrupts cross-table observation) plus 3 lifecycle
  tests — category assign/count/orphan, subcategory parent line,
  brand assign/count/orphan.

Local evidence: escaped-quote grep = 0, braces balanced, and
strings.xml parses on every changed file (re-checked after the
staging copy). First verification is CI, as before.

Verification (2026-10-23): all three workflows green on the exact
head `9812502` (pull_request events, PR #24): Native Android CI run
39934861069 — success; Compose UI Emulator Tests run 39934861077 —
success; Phase 12 Segmented Verification run 39934861178 — success.
Package 15.3 ACCEPTED on `9812502`.

## Package 15.4 — media references (§22) — PENDING CI

What was wired:

- **`Item` gains media references**: `mainImageUri: String?` and
  `additionalImageUris: List<String>` — URI strings in the payload
  only; no binary ever enters an operational record.
- **`ItemJson`**: encodes the list as a JSON array of strings;
  decode tolerates a lone string as a one-element list and drops
  blanks and non-strings (a value counts only when it is a string
  primitive).
- **`ItemFormState`**: references edited as text — the main URI
  trimmed-or-null; additional URIs one per line, blanks dropped.
  Form fields for both, and the list shows an "Image attached"
  line when a main reference is present.
- **Deliberate absence, stated plainly**: no rendering and no
  thumbnails. The app has no image-loading library; pulling one in
  is a separate owner-level decision, so references and the editor
  ship and display does not — nothing is faked.

Tests:

- `ItemJsonTest` +2: media round-trip (main + additional list);
  tolerant decode (lone string, blanks and non-strings dropped).
- `ItemFormStateTest` +1: per-line editing of additional URIs and
  main-URI trim/null handling.
- `ItemsScreenComposeTest` +1:
  `imageReferencesSaveThroughCodecAndShowInList` — references save
  through the codec and the list line appears.

Local evidence: escaped-quote grep = 0, braces balanced, and
strings.xml parses on every changed file (re-checked after the
staging copy). First verification is CI, as before.
