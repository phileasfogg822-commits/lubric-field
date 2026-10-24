# Phase 19 — Inventory Ledger & Stock Ops (Roadmap V2)

Branch: `phase-19-inventory-ledger` (from the Phase 18
candidate, `phase-18-quotations-sales-orders` at `42ddb48`).
Draft PR to follow via the connector. Packages are accepted
only with green-CI proof on the exact SHA, as in Phases 12–18.

## Authoritative scope

Master spec §180 (inventory ledger: stock is derived from
movements, never from `item.quantity` alone), §181
(immutability: corrections are reversing movements), §31
(inventory screen: computed value / item / low-stock cards and
filters), §32 (stock status), §33 (minimum stock & reorder
level), §36 (stock adjustment, reason required), §37 (stock
count with variance), §35 (stock transfers, paired movements),
§30 (serial number tracking), §175 (negative-stock policy as a
business setting). This phase also consumes the Phase 17.4
posting contract's `StockMovement` vocabulary — it is the
contract's first real producer — and closes the Phase 16
deferrals (§30 serials, §35 transfers).

Movements live in the Phase 12 operational store as
"stock_movements" records — already in the sync whitelist AND
INSERT_ONLY in the sync queue, so the ledger is append-only by
platform rule. Transfer headers ("stock_transfers") and serial
records are NOT whitelisted → local-only, the numbering /
timeline posture from Phase 18; stock truth still syncs
because it lives in the movements.

Standing decisions, pinned at branch time (from the phase
prep): the balance engine takes each item's Phase 15
`openingStock` datum as its starting constant — no OPENING
movement is ever fabricated on first read (fake history). The
16.4 stored batch quantity stays a datum and is never summed
into availability; batch balances derive from movements
carrying a batchId. §213 stock reservation is deferred to
Phases 21/22 (it binds to orders/invoices), recorded here, not
silently dropped. The negative-stock default at the UI is Warn
until a settings home exists; the engine takes the policy as
an input and never hard-codes one behaviour.

## Package split

- **19.1** Movement model + codec: the typed stock-movement
  record over "stock_movements", aligned with the 17.4
  vocabulary, + the typed ViewModel surface and the
  `postMovement` write API later packages post through.
- **19.2** Ledger engine (domain/inventory): balances per
  item (warehouse / batch dimensions) from movements, §32
  status from balance + §33 levels, §175 policy as an input.
- **19.3** Inventory screens: the typed §31 screen (computed
  cards, filters, per-item movement history) replacing the
  raw Phase 12 screen.
- **19.4** Adjustments + stock count (§36/§37): adjustment
  form posting movements; count mode with variance → adjust.
- **19.5** Transfers (§35): header + lines, status flow,
  paired TRANSFER_OUT / TRANSFER_IN movements.
- **19.6** Serials (§30): serial registry records, receive /
  adjust flows, per-item serial list. Sale-time serial removal
  lands with Phase 21 document posting.
- **19.7** Phase gate: evidence sweep + close-out +
  Phase 20 prep.

## Package 19.1 — Movement model + codec

Status: ACCEPTED on `facc961` (fix head).

`StockMovementRecord` over "stock_movements" — signed quantity (the §180 ledger convention), `MovementType` (OPENING / PURCHASE / SALE / RETURN / ADJUSTMENT_IN / ADJUSTMENT_OUT / TRANSFER_IN / TRANSFER_OUT) with `isInbound` direction, warehouse/batch dimensions, reference document type + id, rate, reason, createdAt; `of()` applies the sign from the type so writers cannot write a positive SALE. `StockMovementJson` tolerant codec: unknown/missing type → ADJUSTMENT_IN with quantity ZERO (historical Phase 12 raw rows change no balance, and the type is never guessed). `toPostingMovement(currency)` bridges to the 17.4 vocabulary (positive quantity, kind from type). `InventoryViewModel` gains the typed surface (observe/load movements sorted for the ledger, per-item load) and `postMovement` — the append-only write API 19.4/19.5/Phase 21 post through. There is deliberately no movement update/delete (§181: corrections are reversing movements). feature/inventory gains the `:domain:accounting` dependency.

Tests (10): StockMovementRecordTest (7 — codec round-trip, record id wins, garbage → zero movement, unknown-type neutrality, sign-from-type, posting bridge, sign-mismatch detection); InventoryMovementViewModelTest (3 — save/load round trip, postMovement signs/stamps/appends, per-item filter + ledger sort).

Failure history: first head `de5ed51` failed all three workflows at `:feature:inventory:compileDebugKotlin` — StockMovementJson's kotlinx symbols unresolved: feature/sales declares `kotlinx-serialization-json:1.6.2` explicitly and inventory never did. Fix head `facc961` adds the same line.

Verification (GitHub Actions on exact head `facc961`, PR #28): Native Android CI run 40047134040 — completed success; Compose UI Emulator Tests run 40047134042 — completed success; Phase 12 Segmented Verification run 40047134079 — completed success. 19.1 ACCEPTED.

## Package 19.2 — Ledger engine

Status: ACCEPTED on `93b0e97`.

`StockLedger` in domain/inventory (pure rules, no Gradle changes): `balances` grouped per (item, warehouse, batch) from signed `LedgerMovement`s, with the per-item opening constant applied to the warehouse-less, batch-less dimension only — never a fabricated OPENING movement, and the 16.4 stored batch quantity is never summed in. `totalFor` / `balanceFor` for the item total and one exact dimension. §32 `status` — INACTIVE (caller-flagged) first, then OUT_OF_STOCK at/below zero, OVERSTOCK above a set maximum, LOW_STOCK at/below the §33 minimum. §33 `needsReorder` at/below the reorder level, falling back to the minimum when no reorder level is set. §175 `checkNegative` takes the policy as an input (NEVER_ALLOW → BLOCKED, WARN → ALLOWED_WITH_WARNING, ALLOW → ALLOWED) — no behaviour is hard-coded.

Tests (6): the §180 worked example (Opening +100, Purchase +20, Sale −5, Return +1, Adjustment −2 = 114), opening-constant placement, warehouse/batch split, §32 derivation incl. inactive and no-levels cases, reorder fallback, the policy matrix.

Verification (GitHub Actions on exact head `93b0e97`, PR #28): Native Android CI run 40048747861 — completed success; Compose UI Emulator Tests run 40048747857 — completed success; Phase 12 Segmented Verification run 40048747863 — completed success. 19.2 ACCEPTED.

## Package 19.3 — Inventory screens

Status: ACCEPTED on `2968f8c`.

The typed §31 inventory screen replaces the raw org.json Phase 12 screen (signature kept for `AppModuleRegistry`). `InventoryReferenceData` reads "products" tolerantly into `ItemStockOption` (name fallback to id, purchaseRate, openingStock) plus named options for "warehouses" / "categories" / "brands" — feature/inventory deliberately does not depend on feature/items. `StockLevelsRecord` + `StockLevelsJson` over "stock_levels" (local-only, not whitelisted): the §33 levels home is its own record keyed by itemId, since the stored Item record carries no min/reorder/max/active fields. `InventorySummaryBuilder` builds rows through the 19.2 `StockLedger` — total balance, or the single selected warehouse's dimension when filtered (opening excluded there); value = balance × purchaseRate, null when no rate. Summary cards: total value (Σ of row values), item count, low-stock count (LOW_STOCK rows only); rows sorted by lowercase name. Filters: status / warehouse / category / brand chips. Item detail shows the balance, §32 status, the movement history, and the §33 levels editor — its Save button sits in a fixed area outside the LazyColumn (the 18.4 stale-handler lesson). `ItemDetail` is a `ColumnScope` extension so `Modifier.weight(1f)` compiles.

Tests (7): InventorySummaryTest (5 — row balance incl. opening, warehouse-dimension balance, value null without a rate, low-stock counting, name sort); InventoryScreenTest (2 Compose — seeded product + SALE movement renders "Engine Oil · 45 · In stock" and the value card "Inventory value: 4500"; detail shows "Balance: 45" and the movement history line).

Verification (GitHub Actions on exact head `2968f8c`, PR #28): Native Android CI run 40050183827 — completed success; Compose UI Emulator Tests run 40050183810 — completed success; Phase 12 Segmented Verification run 40050183865 — completed success. 19.3 ACCEPTED.

## Package 19.4 — Adjustments + stock count

Status: ACCEPTED on `e75cf9a`.

`StockAdjustment.kt`: `StockAdjustmentKind` (OPENING_STOCK→OPENING, STOCK_IN→ADJUSTMENT_IN, STOCK_OUT/DAMAGE/EXPIRED/LOST→ADJUSTMENT_OUT, FOUND→ADJUSTMENT_IN, MANUAL_CORRECTION→null with direction chosen via `manualInbound`), `StockAdjustmentFormState` (valid ⇔ item chosen, quantity parses > 0, reason non-blank — the §36 requirement; optional attachmentUri), `AdjustmentResult` sealed (Posted(movement, warning) / BlockedByPolicy), `StockCountRow` (§37: variance = counted − expected; varianceMovementType null at zero/unparseable). `StockMovementRecord` + codec extended additively with optional `attachmentUri`; `postMovement` gained the parameter. ViewModel `postAdjustment(scope, form, policy = WARN)` and `postCountVariance(scope, itemId, variance, warehouseId, date, reason, policy = WARN)` share a private `postChecked`: for outbound types it loads movements + the item's opening, computes the balance of the exact dimension (total incl. opening when no warehouse), runs `StockLedger.checkNegative` (BLOCKED → BlockedByPolicy; warning flagged), then posts with referenceType "STOCK_ADJUSTMENT". Screens gained LIST/ADJUST/COUNT modes: Adjust mode = clickable item rows, kind chips, manual direction toggle, quantity/date/reason/attachment fields, warehouse radios, Post button outside any lazy container; Count mode = one reason field, per-item Expected text / Counted field / Variance text, per-row "Adjust stock" shown only when varianceMovementType != null.

Tests (9): StockAdjustmentTest (6 unit — kind mapping, form validity incl. blank-reason rejection, postChecked posts signed movement, NEVER_ALLOW blocks outbound, count variance sign mapping, attachmentUri round-trip) + InventoryScreenComposeTest.adjustFlowPostsAMovement (clicks "Adjust stock", item row "Engine Oil", types Quantity 3 / Reason "Leaking cans", clicks "Post adjustment"; asserts "Adjustment posted" and saved quantity +3 with the reason — alongside the 19.3 tests still green).

Failure history: first head `342a027` went Segmented-green but Compose failed on adjustFlowPostsAMovement with "Failed to perform text input"; fix head `19d1baf` (added performScrollTo) failed again in Compose — the scroll hypothesis disproved. Root cause proven from the run's compose-ui-emulator-reports artifact XML: `Action performScrollTo() failed ... could not find any node ... 'Quantity'` — LazyColumn does not compose below-fold items, so the adjust form's fields were absent from the semantics tree (recorded as a new AGENTS.md lesson). Real fix head `e75cf9a` converted the form container from LazyColumn to a vertically scrolling Column so every node stays composed.

Verification (GitHub Actions on exact head `e75cf9a`, PR #28): Native Android CI run 40054664091 — completed success; Compose UI Emulator Tests run 40054664112 — completed success; Phase 12 Segmented Verification run 40054664101 — completed success. 19.4 ACCEPTED.

## Package 19.5 — Transfers

Status: ACCEPTED on `6aaf3c4`.

`StockTransfer.kt`: `StockTransferRecord` over "stock_transfers" (local-only, not whitelisted — one record per transfer with the lines EMBEDDED in the header payload, so status transitions stay atomic; the paired TRANSFER_OUT/TRANSFER_IN movements in the sync-whitelisted ledger carry the stock truth), `TransferStatus` DRAFT/DISPATCHED/RECEIVED/CANCELLED, `StockTransferLine(itemId, quantity)`, `StockTransferFormState` (per-item quantity map — the count pattern, so no duplicate lines per item; valid ⇔ from ≠ to, both chosen, ≥1 positive line), `TransferResult` sealed (Done(transfer, warning) / BlockedByPolicy / InvalidState), `StockTransferJson` tolerant codec (unknown status → DRAFT; unparseable line quantity → ZERO, dispatch skips non-positive lines). ViewModel: `outboundDecision(...)` extracted from `postChecked` (19.4 behavior unchanged); `observeTransfers/loadTransfers/saveTransfer` (sorted date+createdAt+id), `createTransfer` (null on invalid form), `dispatchTransfer` — pre-checks ALL lines before posting anything: totals grouped per item, each §175-checked via `outboundDecision` at the source warehouse (BLOCKED → whole dispatch blocked, never half-dispatched), then posts TRANSFER_OUT per line with referenceType "STOCK_TRANSFER" + referenceId = transfer id; `receiveTransfer` posts TRANSFER_IN at the destination; `cancelTransfer` DRAFT-only (a dispatched transfer must be received — its outbound movements already exist). Screen: `InventoryMode.TRANSFERS`; transfer rows `"%1$s → %2$s · %3$s"` (from name → to name · status label); detail shows lines and status actions (Dispatch+Cancel on DRAFT, Receive on DISPATCHED) outside any lazy container; `TransferEditor` and the adjust form both use scrolling Columns so test-editable fields stay composed.

Tests (9): StockTransferTest (8 — codec round-trip + garbage/unknown-status tolerance, form validity, create draft/invalid, dispatch posts −20 TRANSFER_OUT at source + flips status + second dispatch InvalidState, NEVER_ALLOW dispatch posts nothing and stays DRAFT, receive posts +20 TRANSFER_IN at destination + receive-from-DRAFT InvalidState, cancel only from DRAFT) + InventoryScreenComposeTest.transferDispatchFlowPostsOutMovement (seeds warehouses + PURCHASE +50 at wh-main + DRAFT transfer; clicks "Transfers", row, line, "Dispatch"; waits for 2 movements; asserts "Transfer dispatched" and the saved TRANSFER_OUT −20 at wh-main with referenceId tr-1).

Failure history: first integration head `c44cf94` failed all three workflows with 8 Kotlin compile errors in InventoryScreen.kt — the earlier staging-corruption repair script had restored `TransferDetail`'s header with only `onDispatch`, dropping the `onReceive`/`onCancel` parameters the call site and body need (a shape-check gap: static checks verify braces/imports/strings, not Kotlin type resolution). Fix head `6aaf3c4` restores both parameters (2 lines); the same fix was applied to the 19.5 and 19.6 staging copies so it cannot recur at 19.6 integration.

Verification (GitHub Actions on exact head `6aaf3c4`, PR #28): Native Android CI run 40056641392 — completed success; Compose UI Emulator Tests run 40056641304 — completed success; Phase 12 Segmented Verification run 40056641387 — completed success. 19.5 ACCEPTED.

## Package 19.6 — Serials

Status: ACCEPTED on `ead5f69`.

`StockSerial.kt`: `StockSerialRecord` over "serial_numbers" (local-only, not whitelisted — per the pinned prep decision), `SerialStatus` IN_STOCK/SOLD/REMOVED, fields itemId/serialNumber/warehouseId?/date/note/createdAt; `StockSerialJson` tolerant codec (unknown status → IN_STOCK; record id wins); `SerialAddResult` sealed (Added(record) / Duplicate / Invalid). Serials are never deleted — they leave by status, keeping the audit trail. ViewModel: `observeSerials/loadSerials/loadSerialsForItem/saveSerial` (sorted itemId+serialNumber+id), `addSerial` (trims; blank → Invalid; Duplicate ⇔ same item already holds the same serial IN_STOCK — same serial on a different item, or re-registering after it left, is a new record), `removeSerial(serialId, reason)` (IN_STOCK → REMOVED with the reason stored in `note`; null for unknown/not-in-stock). Screen: serials live in the item detail — `ItemDetail` gained `serials`, `onAddSerial`, `onRemoveSerial` params; the IN_STOCK serial list rides inside the existing history area as display rows with per-row Remove buttons (acting on the row's immutable id only), while the Add form (Serial number field + "Add serial" button) sits in the fixed area; messages added/duplicate/invalid; write-off reason is the string resource `inventory_serial_writeoff_reason` ("Serial written off").

Tests (5): StockSerialTest (4 — codec round-trip incl. data-class equality + garbage/unknown-status tolerance, add registers IN_STOCK + blank rejected + per-item filter, duplicate rules, remove writes off with note + double-remove null + re-registration keeps both records) + InventoryScreenComposeTest.serialAddAndRemoveFromItemDetail (seeds product "tv" `{"name":"Samsung TV"}` with no openingStock ⇒ row "Samsung TV · 0 · Out of stock"; scrolls to "Serial number", types "SN28282", scrolls to and clicks "Add serial", waits for 1 record, asserts "Serial added" and count 1; clicks "Remove", waits for status REMOVED, asserts count 0).

Failure history: first integration head `a5a0db5` failed all three workflows with Kotlin "platform declaration clash" — the ViewModel's two private `sortedForDisplay` member-extensions (transfers vs serials) erase to the same JVM signature. Fix head `bf7f532` renamed the serial one to `sortedSerialsForDisplay` (Native + Segmented green, Compose red: 2 ItemDetail tests). Root cause from the compose-ui-emulator-reports artifact XML: the 19.6 additions made ItemDetail's fixed content tall enough to squeeze the history LazyColumn to zero height on small screens (nodes in tree, not displayed) — history line assertion failed and the serial add tap silently did nothing. Fix head `efb2eed` converted ItemDetail to a vertically scrolling Column with all rows composed (history test fixed; serial test still red — its form now lives in a scrolling container but the test typed/tapped without scrolling first). Fix head `ead5f69` (test-only) added `performScrollTo()` before the serial field input and button tap, mirroring the 19.4 adjust test. The same ItemDetail fix was applied to the 19.6 staging copy (verified byte-identical).

Verification (GitHub Actions on exact head `ead5f69`, PR #28): Native Android CI run 40061481216 — completed success; Compose UI Emulator Tests run 40061481202 — completed success; Phase 12 Segmented Verification run 40061481196 — completed success. 19.6 ACCEPTED.

## Package 19.7 — Phase gate

Status: NOT STARTED.
