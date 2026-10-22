# Phase 12 — completion gate (2026-10-19)

Branch: `phase-12-completion`; draft PR #12. Do not merge until all gates pass.

## Verified CI evidence
- GitHub commit `fd5cf51f`: CircleCI statuses `phase12-smoke` and `android-full-verification` both reported success.
- CircleCI workflow: https://app.circleci.com/workflow/63af6be8-f9fd-4e9b-b25e-baff6459f3f9
- The job statuses are not a substitute for inspecting individual Gradle steps, test reports, and APK artifacts.
- Legacy GitHub Actions still report failures; do not disable until parity is demonstrated.

## Outstanding acceptance gates (7)
- [ ] **12-A: CI evidence:** Inspect full CircleCI step logs, test XML, lint report, debug/release/white-label APK artifacts; verify distinct APKs and hashes.
- [ ] **12-B: Android UI:** Implement and wire functional Compose screens for items, inventory, parties, sales, purchases and accounts; add automated UI/navigation tests. Current branch audit confirms all six Compose screen files exist and are imported by MainNavigation; they expose scoped repository-backed entry/list flows. Full CRUD and automated UI acceptance remain unverified.
- [ ] **12-C: Durable sync:** Add regression tests for restart, idempotent replay, receipt handling, server retry-after, cursor monotonicity, rollback and conflicting local edits.
- [ ] **12-D: Cloud consent and security:** Test mid-flight opt-out, no-network local-only mode, tenant switching, revocation, expired and corrupt credentials, and log redaction.
- [ ] **12-E: Desktop parity:** Verify Tauri SQLite outbox/cursors, schema parity, replay and multi-device conflict contracts against Android/backend.
- [ ] **12-F: Complete cloud acceptance:** Run all applicable tests, lint, instrumentation-test compilation, debug/release and white-label builds on the same candidate commit; preserve reports and artifacts.
- [ ] **12-G: Release review:** Reconcile CI parity, retire duplicate GitHub Actions only after replacement verified, complete PR review and merge only after explicit authorization.

## CI cost policy
Keep the smoke job on pushes. Run expensive full verification intentionally using the CircleCI `run_full_verification=true` parameter; see CircleCI project pipeline trigger settings. If a GitHub App trigger overrides this parameter, validate actual workflow behavior and adjust trigger configuration before assuming cost limits.

## Current active work
12-B current audit: all six operational screen files exist and MainNavigation imports them (head 0f1cccb). Entry/list implementations are present; full CRUD and UI automation are not yet accepted. Next: validate navigation and missing CRUD behavior, then add targeted UI/Room tests. Phase 12C remains open for recovery, receipts, consent races and cross-device parity. See Issue #19 for sequential task queue.

## 2026-10-19 12B source reconciliation
- Source inspection at `0f1cccb`: `ItemsScreen`, `InventoryScreen`, `PartiesScreen`, `SalesScreen`, `PurchasesScreen`, and `AccountsScreen` all exist, use Compose and scoped ViewModel observation, and are imported by `MainNavigation.kt`.
- These screens show entry/list functionality, **not verified full CRUD**. Do not claim edit/delete or automated navigation acceptance without tests.
- GitHub Actions `39250508080` (Native Android CI) and `39250508036` (Phase 12 Segmented Verification) succeeded on the same commit.
- PR #12 stays draft and unmerged; follow sequential Issue #19 for remaining verification and implementation.

## 2026-10-20 GitHub Actions reconciliation and current 12B acceptance

GitHub Actions is the sole active CI provider for this phase; earlier CircleCI-focused guidance above is historical and superseded. The last fully verified head before the current guard-test commit was `194b81a8`, with [Native Android CI 39370720005](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39370720005) and [Phase 12 Segmented Verification 39370720021](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39370720021) both successful.

- **Verified implementation:** all six operational screens have scoped offline create/list/edit flows. `194b81a8` adds a six-module Room restart regression covering preserved fields, isolation and CREATE/UPDATE queue operations.
- **Deletion safety:** `37d6ea92` adds a regression asserting incoming DELETE cannot remove local data or advance the cursor until an explicit tombstone contract is implemented. Its CI outcome must be checked before accepting the new test. Do not implement hard-delete on financial or stock records without backend and desktop contract agreement.
- **Still open:** end-to-end safe delete/tombstone behavior, Compose navigation/backstack/loading/error/empty/localization tests, broader Room CRUD/restart/scope tests and inspection of same-commit Android lint, test reports and debug/release/white-label APK artifacts.
- **12C:** receipt/retry/cursor/conflict/consent/security/desktop parity acceptance remains tracked in Issue #19; do not mark complete from green compilation alone.
- **Release:** draft PR #12 must remain unmerged until explicit approval.


## 2026-10-22 Packages 1–2 verification (head `17e77f4c`)

First fully green candidate head. All four GitHub Actions workflows succeeded on the same commit `17e77f4c09d6456aaea0615ab0a6e458a2823a75`:

- [Native Android CI 39757736679](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39757736679) — all steps green: boundary guards, 15.1 Room regression, 15.2 full Room store tests, 15.3 all Android unit tests, 15.4 lint, 15.5 debug APK, 15.6 release APK, 16 migration-test compilation, 17 white-label APK, 17.1 Compose instrumentation compilation, 18–19 artifact evidence recorded and uploaded.
- [Phase 12 Segmented Verification 39757736743](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39757736743) — 20/20 jobs green.
- [Compose UI Emulator Tests 39757736547](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39757736547) — green, including the new Parties delete and Cloud Sync pairing Compose tests.
- [Desktop and VPS Verification 39757736799](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39757736799) — green. This workflow was already failing on the pre-package head `9d4c7f4` (verified against the 2026-10-21 runs): the push-handler contract test's protocol double had gone stale when the handler gained stored-field replay comparison. The double is repaired (`b5fc0824`); the production handler was not changed. All 8 scenarios pass.

### Accepted with this evidence (Issue #19)
- **12B-04 Parties — accepted.** Soft-delete parity delivered (`510723b2`): customers are nonfinancial master data, so Parties gained the Items-style tombstone delete (ViewModel, confirm dialog, strings) with ViewModel and Compose tests. The DELETE-contract audit concluded the other modules must NOT gain delete: `orders`, `purchase_orders`, `accounts`, `stock_movements` are rejected by the repository contract ("financial and inventory history requires a reversal, not deletion").
- **12B-11 — accepted.** Same-SHA tests, lint, debug/release/white-label APK builds and artifact evidence recorded above.
- **12C-02 — accepted.** SyncEngineTest now covers missing (empty), unknown, duplicate and partial receipts plus idempotent acknowledgment; unknown receipts still cannot acknowledge local changes.
- **12C-03 — accepted.** Persisted FAILED/backoff state across restart (pre-existing) plus new elapsed-backoff resumption: the operation becomes pending again and a full engine round marks it SYNCED.

### Partially verified
- **12C-06 pairing — partial.** Delivered: `CloudPairingGateway`/`DevicePairingManager` (HTTPS + non-blank validation before any state change, Keystore-only token storage, per-scope scheduling, re-pair cancels the previous scope), Cloud Sync pairing screen (write-only token field), startup restore, lifecycle tests (validation, record/schedule side effects, restore, unpair). Remaining: expired-token and corrupt-token behavior tests (the Keystore crypto round-trip is not Robolectric-coverable; `token()` already fails safe to null) and a log-redaction test.
- **12C-07 opt-out race — partial.** Scheduler LOCAL_ONLY cancellation, worker zero-network-with-consent-off, transport fail-fast consent check and per-request consent re-check are all tested and green. Remaining: a deterministic mid-flight revocation race test, which needs a connection seam in `AuthenticatedVpsTransport` (it opens `HttpURLConnection` directly).

### Still open
12B-02/03/05/06/07 (delete is intentionally absent for financial modules; remaining flow acceptance), 12B-08, 12B-09 (navigation/back-stack tests), 12B-10 (broader CRUD/isolation), 12C-01, 12C-04, 12C-05, 12C-08, 12C-09, 12C-10, 12C-11. PR #12 stays draft and unmerged.

## 2026-10-22 Package 3 verification (12C-05 / 12C-08)

Pushed as a single commit `13f831f3a8ab7ce8aa7c11d533dd64d8aa59fa1c` after a full
test-coverage audit of 12C-04/05/08. All four workflows are green on that head:
[Native Android CI 39760866892](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39760866892),
[Segmented Verification 39760866900](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39760866900),
[Compose UI Emulator 39760866903](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39760866903),
[Desktop/VPS 39760866966](https://github.com/phileasfogg822-commits/lubric-field/actions/runs/39760866966).

Defect found and fixed (12C-05): the Android incoming path
(`RoomSyncQueueStore.applyRemoteAndSaveCursor`) upserted every replayed row, so
a pull replaying a ledger UUID with different data would have overwritten the
stored posting. The VPS push handler and the desktop agent both treat
`account_transactions`, `cash_movements`, `stock_movements` and `party_ledger`
as insert-only; Android now skips an incoming change for those tables when a
local record already exists (the rest of the batch and the cursor still
advance). Proven by `incomingFinancialReplayCannotOverwriteLocalLedgerRecord`.

Advanced but still partial:

- **12C-05** — Android insert-only parity fixed and tested; server gained the
  stale-editable-update scenario (a second device's older `updated_at` write is
  rejected, not applied; 9/9 scenarios pass). Remaining: a genuine two-device
  simulation and the remote-change-vs-already-synced-local-edit policy.
- **12C-08** — dynamic isolation proven: `markAccepted`/`markRetry` from a
  different device are ignored (`queueAcknowledgementAndRetryIgnoreOtherDevices`),
  and cancelling one identity's scheduled sync leaves another identity's job
  ENQUEUED (`cancellingOneIdentityLeavesAnotherIdentityScheduled`). Remaining:
  cross-scope sync-run isolation, `isPaired` device-mismatch, and a
  switch-then-operate sequence test.
- **12C-04** — unchanged by this package. Store-level rollback, cursor
  monotonicity and replay idempotency were already proven; the open gaps are
  transport-level pull validation and process-death-mid-pull, both needing a
  connection seam in `AuthenticatedVpsTransport` (shared with the 12C-07
  mid-flight race residual).

## 2026-10-22 Package 4 verification (transport seam; 12C-07 accepted)

`AuthenticatedVpsTransport` gained a `connectionFactory` seam (Package 4,
`73dc31e8`) with Robolectric exchange tests driving scripted connections:
happy-path push/pull receipts, **mid-exchange consent revocation stops before
the pull request is opened**, regressing server cursor rejected, unexpected
pull table rejected, malformed stored cursor rejected before any request.
The first CI round failed at `:core:store` compilation — the new last
constructor parameter silently re-bound the trailing consent lambda at the
`CloudSyncScheduler` call site; fixed by binding `canExchange` by name
(`afeeb768`). All four workflows are green on head `afeeb768` (Native Android
CI, Segmented Verification, Compose UI Emulator, Desktop/VPS).

- **12C-07 ACCEPTED.** Scheduler LOCAL_ONLY cancellation, worker zero-network
  with consent off, transport fail-fast + per-request consent checks, and now
  the deterministic mid-flight revocation race are all proven green.
- **12C-04 advanced, still partial.** Transport-level pull validation is
  closed. Remaining: process-death-mid-pull and a composed engine + real Room
  store interruption round.

## 2026-10-22 Package 5 verification (12B-08 accepted) + Package 5b (12B-09) shipped

Package 5 (`eb2fef6c`) added a localized empty-state line to all six module
screens (Items, Inventory, Parties, Sales, Purchases, Accounts) and per-module
Compose tests for the empty and save-error states; its first CI round failed on
a literal escaped-quote defect in the six test fakes, fixed in `19a94dea`.
**All four workflows are green on head `19a94dea`** (Native Android CI,
Segmented Verification, Compose UI Emulator, Desktop/VPS).

- **12B-08 ACCEPTED (with a design note).** Empty state and save-error state
  are proven per module on the emulator. Loading shares the empty surface in
  this Flow design (the screens collect with an empty-list initial value and
  have no separate loading indicator); no spinner was invented for the test.
- **12B-09 in verification.** Package 5b (this commit) adds
  `MainNavigationComposeTest` — the app module's first instrumented tests —
  covering the four bootstrap states of the state-based `MainNavigation`
  (loading indicator, onboarding when no business exists, error message on a
  failing bootstrap flow, dashboard with all seven feature entries when
  ready) and wires `:app:connectedDebugAndroidTest` into the emulator
  workflow. Feature click-through is not covered: feature screens resolve
  their own Hilt ViewModels internally, so a full click-through needs the
  Hilt graph; that residual is recorded rather than hidden.

## 2026-10-22 Package 6 (12C-01 audit write-up; 12C-09 / 12C-10 contract fixture) — VERIFIED (see the verification section below)

Prepared locally; **not accepted** until this package is pushed and all four
workflows are green on the resulting head.

- **12C-01 table parity — audit write-up (verified in source, pending CI).**
  The three production table sets were compared exactly and are identical:
  Android `RoomSyncQueueStore.SUPPORTED_TABLES` == server `SYNC_TABLES`
  (`server/index.js`) == desktop `SYNC_TABLES` (`src/lib/sync.ts`) — the same
  28 tables: account_transactions, accounts, cash_movements, commitments,
  customer_groups, customers, expenses, inspections, order_items, orders,
  party_ledger, party_price_lists, party_prices, price_list_items,
  price_lists, products, purchase_orders, reminders, reservations, services,
  stock_movements, unit_conversions, units, vehicles, vendor_payables,
  vendors, warehouse_locations, warehouses. The insert-only ledger set is
  likewise identical in all three implementations — `account_transactions`,
  `cash_movements`, `stock_movements`, `party_ledger` (Android
  `INSERT_ONLY_TABLES`, server `immutableLedger`, desktop
  `INSERT_ONLY_SYNC_TABLES`). The deliberate exclusions also agree:
  `commitment_items` (no `shop_id` yet, so the pull cannot scope it) and the
  derived `stock_levels` cache are absent from every client's set.
- **12C-10 cross-client contract fixture.** New canonical fixture
  `sync-contract/sync-contract.json` captures the wire contract all three
  clients implement: the 28-table supported set, the insert-only ledger set,
  the locked receipt rules (exact operation IDs; an outbox entry is deleted
  only on an explicit server ack of that exact ID — missing, unknown,
  UUID-only and conflicting receipts all retain the entry), a canonical push
  exchange, six canonical receipt scenarios, and canonical pull exchanges
  with a monotonic cursor (a regressing cursor aborts the round and
  preserves the stored cursor; batch and cursor commit atomically; an
  insert-only replay never overwrites the stored posting). Consumers:
  `server/test_sync_push.mjs` pins the fixture to the production handler's
  own table configuration and to the desktop source, and drives the
  fixture's canonical push through the production handler;
  `scripts/test-desktop-sync.mjs` executes every fixture receipt scenario
  plus the pull exchanges against the production desktop agent and pins
  the production desktop constants to the fixture; Android
  `SyncContractFixtureTest` (core:store) asserts its test-resources copy is
  byte-identical to the canonical file and drives the fixture pull
  exchanges through the production Room queue store.
- **12C-09 desktop/Tauri SQLite parity.** The desktop leg of the contract
  continues to be exercised against real on-disk SQLite by
  `scripts/test-desktop-sqlite.mjs` (durable commit across reopen, real
  rollback preserving rows and cursor, insert-only ledger replay, complete
  retry); Package 6 ties those behaviors to the shared fixture above
  instead of desktop-only constants. Unchanged residual, recorded in that
  file: the Node adapter uses one SQLite connection, so native Tauri
  plugin-sql connection-pool atomicity still needs on-device verification
  and is not claimed here.
- **Local evidence (this preparation run, 2026-10-22):** `node
  server/test_sync_push.mjs` 14/14 PASS, `node scripts/test-desktop-sync.mjs`
  30/30 PASS, `node --disable-warning=ExperimentalWarning
  scripts/test-desktop-sqlite.mjs` 7/7 PASS. Android cannot compile or run
  locally (no SDK in this environment); `SyncContractFixtureTest` is
  verified only when CI runs it, which is why this whole section stays
  PENDING CI.

## 2026-10-22 Packages 5b + 6 verification (head `6d24c644`; 12C-01 + 12C-10 accepted)

**All four workflows are green on head `6d24c644`** — Native Android CI
(39825932381), Phase 12 Segmented Verification (39825932360), Compose UI
Emulator (39825932347), Desktop/VPS (39825932345). The road there, for the
record: `d91d0d96` failed Native at lint-model dependency resolution
(versionless `ui-test-junit4` in the app module — the Compose BOM was not
applied to its androidTest classpath; fixed `dfb3c1ba`); that head then
failed on ONE real lint error, `ViewModelConstructorInComposable` in the new
navigation test (fixed `0b5746b2` by constructing view models outside
`setContent`); `fc17c8bb` failed Segmented across all core:store jobs
because the fixture test's `seed()` called suspend DAO upserts from a plain
function (fixed `6d24c644` with `runBlocking`). An earlier incident in the
same window: the first three 5b files were briefly stored base64-encoded via
the wrong write route and were restored byte-identical before any of the
above verification.

- **12C-01 ACCEPTED.** The 28-table parity (Android `SUPPORTED_TABLES` ==
  server `SYNC_TABLES` == desktop `SYNC_TABLES`, insert-only set identical
  in all three) is pinned by the shared fixture and asserted in all three
  clients' test suites, green in CI.
- **12C-10 ACCEPTED.** `sync-contract/sync-contract.json` is the single
  cross-client contract: canonical push fully acknowledged by the
  production handler, all six receipt scenarios executed against the
  production desktop `syncNow`, pull legs (apply + cursor advance,
  insert-only replay preserving the original posting, regressing cursor
  rejected) asserted on desktop and against the Room queue store.
- **12C-09 PARTIAL.** Desktop SQLite parity is proven in CI (rollback,
  immutable replay, retry batch). Residual: native Tauri plugin-sql
  connection-pool atomicity still needs on-device verification; it cannot
  be proven in CI and is recorded here rather than hidden.
- **12B-09 PARTIAL.** The MainNavigation bootstrap-state tests compile,
  pass lint, and are in the tree; emulator EXECUTION of `:app` tests still
  requires adding `:app:connectedDebugAndroidTest` to
  `compose-ui-emulator.yml`, which is blocked on the GitHub App's missing
  Workflows permission. Feature click-through (Hilt graph) residual stands
  as recorded in the Package 5 section.

## 2026-10-22 Package 7 (12C-04 composed interruption round) — VERIFIED (see below)

Prepared locally; **not accepted** until this package is pushed and all four
workflows are green on the resulting head. No production code changes — one
new test file:
`core/store/src/test/java/com/lubrcapp/lubrcapp/core/store/repository/ComposedSyncEngineRoomTest.kt`.

- **Placement (dependency-driven).** The composed test needs the real
  `SyncEngine` (core:sync) and the real `RoomSyncQueueStore` + `AppDatabase`
  (core:store) in one class. `core/store/build.gradle.kts` already declares
  `implementation(project(":core:sync"))`, so core:store's test classpath
  sees both; core:sync has no dependency on core:store and cannot gain one
  (store → sync already exists, so sync → store would be a cycle). The app
  module was therefore not needed. core:store already carries Robolectric
  4.11.1, room-testing and the Maven-Central Robolectric repo-url test
  block, so no build file changes either. Precedent:
  `OperationalBusinessRoomIntegrationTest.failedUploadResumesAfterBackoffElapses`
  already composes engine + Room store for the push/retry leg; this package
  adds the missing pull-interruption legs.
- **What the new tests prove** (real engine + real Room store on a seeded
  database; only the transport is scripted):
  1. `pullFailingMidBatchLeavesRecordsAndCursorUnchangedThroughEngine` —
     a pull whose batch the store must reject halfway (second row's payload
     UUID does not match its entity ID, after the first row was already
     upserted inside the transaction) fails the engine round and leaves
     local records AND the stored cursor exactly as before: the valid first
     row is rolled back, the pre-existing record is intact, the cursor
     stays at its prior value. This is atomicity across the composed path,
     not just the store called directly.
  2. `successfulRoundAfterInterruptionAppliesFullBatchAndAdvancesCursorOnce` —
     after that interrupted round, a clean redelivery applies the complete
     batch (the engine resumes from the preserved cursor), advances the
     cursor exactly once, and a further exact redelivery is an idempotent
     no-op: no duplicated records, no second cursor advance.
  3. `pushAckedButCleanupInterruptedReplaysExactOperationIdNextRound` —
     a response that ACCEPTS a push but carries a pull batch that dies
     mid-apply kills the round after the server ack and before the local
     outbox cleanup (the engine persists incoming changes before
     acknowledging outgoing ones, so this is the real crash window). The
     operation stays PENDING with its original attempt count — not lost,
     not half-acked. The next round re-presents the exact same operation
     ID, the server answers DUPLICATE, and the operation settles as the
     single SYNCED row for that ID.
- **What remains of 12C-04.** The mid-apply failure is injected as a batch
  the real store rejects mid-transaction, because once the transport has
  returned, a pull can only fail inside the store's transaction;
  transport-level failure modes occur before apply and are already covered
  by `SyncEngineTest` and `AuthenticatedVpsTransportExchangeTest`. A true
  process-death-mid-pull (killing the process between statements inside
  the apply) is still not directly provable in a JVM/Robolectric test and
  is not claimed: what is proven is the mechanism that makes it safe —
  records, inbox and cursor commit in one Room transaction, and the
  composed engine path performs no non-transactional side effect before
  that commit — plus the ack-after-crash recovery semantics in test 3.
  12C-04 therefore stays **partial** pending CI verification of this
  package and that stated residual.
- **Local evidence (this preparation run, 2026-10-22):** node suites
  unchanged and green — `server/test_sync_push.mjs` 14/14,
  `scripts/test-desktop-sync.mjs` 30/30,
  `scripts/test-desktop-sqlite.mjs` 7/7. Android cannot compile or run
  locally (no SDK in this environment), so the new tests are verified only
  when CI runs them (`:core:store:testDebugUnitTest` via Native Android CI
  step 15.3 and the Segmented core:store jobs).

## 2026-10-22 Package 7 verification (head `5bc5b5cc`) — 12C-04 composed round proven

**All four workflows green on the first CI run of this head** — Native
Android CI (39832265269), Phase 12 Segmented Verification (39832265468),
Compose UI Emulator (39832265235), Desktop/VPS (39832265278). No fix round
was needed. `ComposedSyncEngineRoomTest` (real `SyncEngine` + real
`RoomSyncQueueStore`) proves: a pull rejected mid-batch rolls records and
cursor back through the composed path; a clean redelivery applies the full
batch and advances the cursor exactly once (third redelivery idempotent);
a push acked by the server but interrupted before local cleanup replays the
exact operation ID next round and settles SYNCED via the DUPLICATE receipt.

**12C-04 remains PARTIAL** on one named residual only: a literal process
kill between statements inside the apply transaction cannot be simulated in
a JVM/Robolectric test. What is now proven is the mechanism that makes it
safe — records, inbox, and cursor commit in one Room transaction with no
non-transactional engine side effect before that commit — exercised through
the real engine and the real store, in addition to the store-level
(Package 2) and engine-level rounds already in the tree.

## 2026-10-22 Package 8 (12C-05 / 12C-06 / 12C-08 residuals) — PENDING CI

Prepared locally; **not accepted** until this package is pushed and all four
workflows are green on the resulting head. No production code changes and no
build-file changes — five new test files:

- `core/store/.../TwoDeviceSyncSimulationTest.kt` (12C-05a + a defect pin)
- `core/store/.../RemoteConflictPolicyTest.kt` (12C-05b)
- `core/store/.../CrossScopeSyncIsolationTest.kt` (12C-08)
- `core/store/.../CorruptPairingTokenTest.kt` (12C-06)
- `core/sync/.../TransportTokenRedactionTest.kt` (12C-06)

### 12C-05 — a production defect found, and the policy pinned

- **DEFECT (owner decision required): a device's own push round cannot
  settle against the production protocol composition.** Each link verified
  in production code: (1) the VPS pull returns every shop row written after
  the device cursor with no origin-device exclusion, and the push handler
  stamps `updated_at` at push time (Android payloads carry none), so the
  pull inside the very exchange that pushes a change echoes that change
  back; (2) `AuthenticatedVpsTransport` maps the echo to a remote change;
  (3) `SyncEngine` applies remote changes before acknowledging outgoing
  ones (deliberate ordering, pinned by Package 7); (4)
  `RoomSyncQueueStore` refuses any incoming change for an entity with a
  PENDING/FAILED local operation — and the just-acked operation is still
  PENDING at that moment. The round throws "Remote change conflicts with
  unsynchronized local edit", the operation stays PENDING with its original
  attempt count, the cursor never advances, and every retry replays the
  identical failure while the server already holds the row.
  `pushRoundCannotSettleBecausePullEchoesOwnChange` pins this behavior as
  executable evidence (two consecutive rounds, server-side state probed
  from the second device's view). Candidate fix seams, in increasing blast
  radius: the store's guard ignores an entity whose pending operation was
  acknowledged in the same response (needs an engine/store contract
  change); the transport or server excludes the origin device's rows from
  pull; the engine acknowledges outgoing changes before applying incoming
  ones (re-opens the ack-loss window Package 7 test 3 pinned — the cursor
  would still protect redelivery, but the ordering was chosen
  deliberately); the store recognizes a self-echo by payload equality and
  applies it idempotently. Choosing among these is an architecture
  decision, not a test decision, so no fix is bundled into this package.
- **Two-device propagation (the legs that work today) is proven.** The
  in-test `InMemoryVpsServer` double mirrors the production handler's
  semantics at the SyncTransport seam — exact operation-ID receipts,
  LWW-by-updated_at stale rejection, insert-only replay rules
  (identical = idempotent accept with no new pull visibility, differing =
  rejected), soft-delete-only deletes, per-shop state, monotonic cursor,
  origin-inclusive pull — and is itself verified directly
  (`serverDoubleMirrorsProductionHandlerSemantics`) so the device tests
  rest on a checked oracle. With changes settled at the server seam, both
  directions propagate through the other device's real engine + Room
  store, per-device cursors stay independent, and a third scope in a
  different business pulls nothing from the shop
  (`settledChangesPropagateBetweenDevicesInBothDirections`). What remains
  of 12C-05(a): the sending device's local settlement, blocked by the
  defect above — the end-to-end two-device proof completes when the
  defect is fixed, and the pinned defect test is the regression test
  that must then be inverted.
- **12C-05(b) policy — pinned, and flagged as an OWNER DECISION.** The
  receiving-side matrix is now fully executable: local edit still pending
  → remote change refused, batch rolls back (Package 1–3 era test);
  local edit already SYNCED, editable table → the remote change
  overwrites the staged record on arrival **even when its updated_at is
  strictly older** — `RoomSyncQueueStore.applyRemoteAndSaveCursor`
  performs no updated_at comparison anywhere, so arrival order decides
  (`alreadySyncedLocalEditIsOverwrittenByOlderRemoteChange`); local
  posting already SYNCED, insert-only table → the local posting wins and
  the cursor still advances
  (`alreadySyncedLedgerPostingIsNotOverwrittenByRemoteChange`). Whether
  arrival-order-wins is the intended policy for already-synced editable
  records is an owner decision: today its correctness rests entirely on
  the server's push-side LWW and pull ordering, because the receiving
  side has no guard of its own once a local edit has been acknowledged.
  No behavior was invented or changed by this package.
- Adjacent observation (not a segment item): a server tombstone reaches
  the Android store through the real transport as an UPDATE carrying
  `deleted_at` (the transport never emits DELETE), so the store's DELETE
  tombstone path is only reachable from transports that emit DELETE
  changes; the staged record keeps the row with `deleted_at` set rather
  than the `_deleted` form `RoomVpsBusinessRepository.list` filters on.

### 12C-06 — corrupt/expired token behavior and redaction, at testable layers

- **Corrupt stored token fails safe at every testable layer**
  (`CorruptPairingTokenTest`): with a valid pairing record but garbage
  credential bytes, `SecureDeviceCredentials.token()` fails safe to null
  → `isPaired` is false (the record itself is intact — corruption is
  credential-scoped), startup restore keeps the scope LOCAL_ONLY (no
  consent, no enqueued cloud job), and `CloudSyncWorker` with consent ON
  fails its run at the credential check instead of proceeding.
- **Expired token**: there is no client-side expiry in this design; an
  expired token is indistinguishable from a valid one until the VPS
  rejects it. That path is proven in `TransportTokenRedactionTest`: a 401
  push response fails the exchange with "VPS synchronization failed
  (HTTP 401)", stops before the pull, and the error text carries no
  token. A missing/blank token fails before any connection is opened.
- **Log/credential redaction** (`TransportTokenRedactionTest`): the sync
  code contains no logging statements at all, so the leak surfaces are
  the request line, headers, body and error text. Across success, 401,
  missing-token and consent-revoked exchanges, the token appears in
  exactly one place — the `Authorization` header — and in no URL, other
  header, request body, returned receipt/change/cursor, or thrown error
  message. Scheduler WorkManager input likewise carries only
  tenant/business/device/endpoint/tables (by construction in
  `CloudSyncScheduler.configure`/`syncNow`; asserted indirectly by the
  scheduler tests' job inspection).
- **Still not coverable, with the precise reason**: the `isPaired`
  device-mismatch case and the re-pair-cancels-previous-scope case.
  `DevicePairingManager` constructs its `SecureDeviceCredentials`
  internally (no injection seam), and under JVM/Robolectric there is no
  AndroidKeyStore provider, so `token()` can never return a value and
  `isPaired`'s true branch — and `pair()` past its validation, which
  calls `saveToken` — are unreachable in tests. The identity-match leg is
  covered via `pairedEndpoint` (`pairedEndpointRequiresMatchingScope`),
  and every false-branch consequence (LOCAL_ONLY restore, worker
  failure) is covered above. Closing this residual needs either a
  credential-store seam in `DevicePairingManager` (production change) or
  an on-device/instrumented test; neither is bundled here.

### 12C-08 — sync-run scope isolation and switch-then-operate proven

- `syncRunForOneScopeLeavesOtherScopeUntouched`: one database, two
  scopes with pre-state (B: staged record + cursor at 4 + a pending op).
  A's composed run uploads only A's operation under A's identity, stages
  A's remote record and advances A's cursor to 9; B's op stays PENDING,
  B's cursor stays at 4, B's record is intact, and A's record does not
  exist under B's scope keys. The reverse run then settles B and leaves
  A byte-identical.
- `switchThenOperateMovesOnlyNewScopeData`: both scopes scheduled
  CLOUD_SYNC; after the switch, `syncNow` enqueues only the new scope's
  one-time job and cancels nothing of the old scope's schedule; the
  composed run under the new scope settles only its operation and
  cursor, leaving the previous scope's operation PENDING and its cursor
  unmoved.
- 12C-08 therefore has no remaining JVM-provable residual; WorkManager
  behavior is asserted through the test driver, as in the earlier
  scheduler packages.

### Local evidence (this preparation run, 2026-10-22)

Node suites unchanged and green — `server/test_sync_push.mjs` 14/14,
`scripts/test-desktop-sync.mjs` 30/30, `scripts/test-desktop-sqlite.mjs`
7/7. Escaped-quote check: 0 occurrences of the sequence in all five new
files. Android cannot compile or run locally (no SDK in this
environment), so the new tests are verified only when CI runs them
(core:store and core:sync unit tests via Native Android CI step 15.3 and
the Segmented core jobs). Known CI risks to watch on first run: the
Robolectric AndroidKeyStore absence landing in `token()`'s fail-safe
catch (the corrupt-token tests depend on it — if a future Robolectric
shipped a keystore shadow, those tests would need the seam instead), and
`TestListenableWorkerBuilder` behavior for `CloudSyncWorker`, which the
Package-era scheduler tests already exercise for the consent-off path.

## 2026-10-22 Package 9 (12C-05 push-settlement fix; pulled-tombstone normalization) — PENDING CI

Prepared locally; **not accepted** until this package is pushed and all four
workflows are green on the resulting head. This package contains the first
production-code changes since Package 4: the fix for the defect Package 8
pinned, plus the tombstone normalization Package 8 flagged as an adjacent
observation. Chosen seam: the smallest one from the Package 8 list — the
store's guard exempts operations acknowledged in the same response. The
transport, the server, and the engine's persistence ordering are untouched.

### The fix (same-response-ack exemption)

- **Interface** (`core/sync/SyncEngine.kt`): `SyncQueueStore
  .applyRemoteAndSaveCursor` gains a fourth parameter,
  `acknowledgedOperationIds: Set<String> = emptySet()`, documented as the
  IDs the server accepted in the same exchange that produced the incoming
  changes. The default keeps every out-of-exchange caller (state-seeding
  test calls) on the unmodified guard.
- **Engine** (`SyncEngine.run`): passes the `accepted` set it already
  computes from this response's receipts (ACCEPTED or DUPLICATE) into the
  apply call. Nothing else in the engine changes — in particular the
  ordering does not move: incoming changes and the cursor are still
  persisted BEFORE `markAccepted`, so the ack-then-crash window pinned by
  Package 7 keeps its exact semantics (a process death between apply and
  markAccepted re-presents the exact operation ID next round and settles
  on the server's DUPLICATE receipt; proven by
  `ComposedSyncEngineRoomTest.pushAckedButCleanupInterruptedReplaysExactOperationIdNextRound`,
  whose scripted rounds never echo the pushed entity, so the exemption
  cannot mask its failure path).
- **Store** (`RoomSyncQueueStore.applyRemoteAndSaveCursor`): the conflict
  guard's pending scan now also requires `it.id !in
  acknowledgedOperationIds`. An operation the server acknowledged in this
  response is still PENDING at apply time, but it is no longer an
  unsynchronized local edit: the incoming change for its entity is applied
  normally — it is either the operation's own pull echo or a newer server
  state, both safe to apply once the server accepted the operation.
  Operations NOT acknowledged in this response (rejected, conflicted, or
  simply not sent) keep the guard exactly as before.
- **Blast radius**: implementations updated — `RoomSyncQueueStore`
  (production) and all five fakes in `SyncEngineTest` (the `Store` class
  plus four anonymous objects). No other `SyncQueueStore` implementation
  exists in the tree. All direct store call sites in core:store tests use
  the default empty set, which is semantically exact for them (they apply
  outside an engine exchange, where no operation was just acknowledged).

### Test changes

- `TwoDeviceSyncSimulationTest.pushRoundCannotSettleBecausePullEchoesOwnChange`
  is **inverted** to `pushRoundSettlesWhenPullEchoesOwnChange`: the push
  round now settles (operation SYNCED, cursor advances, the echo is
  applied — not dropped — and counted as downloaded), a second round is a
  clean no-op, and device B receives the row through its own engine round.
  That is the end-to-end two-device settlement 12C-05(a) was missing; the
  test's doc comment preserves the full defect history.
- New regression, same class:
  `rejectedPushStillConflictsWithPulledChangeForSameEntity` — a push the
  server REJECTS (stale by last-writer-wins) earns no exemption: the same
  exchange's pulled change for the same entity is still refused with
  "Remote change conflicts with unsynchronized local edit", the batch
  rolls back, and the operation stays PENDING with its original attempt
  count. The pre-existing store-level guard test
  (`incomingRemoteChangeCannotOverwritePendingLocalEdit`) is untouched.
- New engine-level pins in `SyncEngineTest`:
  `acceptedOperationIdsArePassedToRemoteApply` (the apply call receives
  exactly this response's accepted set, still before markAccepted) and
  `unacknowledgedOperationsAreNotPassedToRemoteApplyAsExempt` (a CONFLICT
  receipt yields an empty exemption set and the operation stays
  retryable). All 12C-02 receipt tests are unchanged in behavior.
- New end-to-end delete leg, same simulation class:
  `ownDeleteEchoSettlesAndHidesRecordOnBothDevices` — A's delete round
  settles against its own echoed server tombstone, and B's list hides the
  record after its next round.

### Tombstone outcome: defect confirmed in code, fixed by conversion

Package 8's adjacent observation is a real defect, confirmed link by
link: the server pull returns raw rows for its soft-delete tables
(products, services, customers, vehicles, vendors, expenses), including
rows deleted after the device cursor, with `deleted_at` set and no
`_deleted` key; `AuthenticatedVpsTransport` maps every pulled row to an
UPDATE change with the raw row as payload (it never emits DELETE); the
store staged that payload verbatim; and
`RoomVpsBusinessRepository.list`/`observe` filter only on `_deleted` —
so **a pulled tombstone left the record visible in the replica list**.

Fix (conversion at the single staging point, in
`RoomSyncQueueStore.applyRemoteAndSaveCursor`): after the existing
payload validation, a non-DELETE change whose payload carries a non-null
`deleted_at` is staged with `_deleted: true` added — the canonical
tombstone form local deletes already write — in both the staged record
and the inbox value. Nothing else changes: live rows carry `deleted_at`
as JSON null and are not touched, the DELETE-op path and its
approved-table validation are untouched, and no read-path code changes.
Proof: new `PulledTombstoneVisibilityTest` — a server-shaped live row is
listed, its server-shaped tombstone (UPDATE, `deleted_at` set) hides it
from `RoomVpsBusinessRepository.list` while a control row stays visible,
the staged record keeps both `deleted_at` and `_deleted`, and a live row
with `deleted_at: null` is never mistaken for a tombstone. One scope
note: the store's DELETE path also requires the table to be in the
Android deletable set, which excludes `expenses`; the conversion does
not go through that path, so pulled expenses tombstones (the server does
soft-delete expenses) now hide correctly too — Android's local
user-initiated delete guard for expenses is unchanged.

### Semantics preserved / not preserved

- Preserved: apply-before-ack ordering and the ack-then-crash replay
  window; exact operation-ID receipts and validation; the guard for every
  unacknowledged operation; insert-only replay parity; cursor monotonicity
  and batch atomicity (all inside the same Room transaction as before).
- Changed (the fix itself): an incoming change for an entity whose
  operation was acknowledged in the same response is now applied instead
  of refused. This is precisely the defect scenario; no other input
  combination changes behavior.
- 12C-05 status after this package (pending CI): (a) the two-device
  end-to-end settlement is proven by the inverted test plus the
  propagation legs — the segment's remaining item is the (b) policy
  question, which stays an explicit OWNER DECISION as recorded in
  Package 8 (arrival-order-wins for already-synced editable records). The
  fix does not touch that policy: it only exempts the guard for
  operations acknowledged in the same response.

### Local evidence (this preparation run, 2026-10-22)

Node suites unchanged and green — `server/test_sync_push.mjs` 14/14,
`scripts/test-desktop-sync.mjs` 30/30, `scripts/test-desktop-sqlite.mjs`
7/7. Escaped-quote check: 0 occurrences of the sequence in all five
new/modified Kotlin files. Android cannot compile or run locally (no SDK
in this environment), so the production changes and new tests are
verified only when CI runs them (`:core:sync:testDebugUnitTest` and
`:core:store:testDebugUnitTest` via Native Android CI step 15.3 and the
Segmented jobs). CI risk to watch on first run: the interface default
parameter is exercised by ~20 direct three-argument store call sites in
core:store tests — Kotlin inherits the interface default through the
override, and if that assumption failed anywhere it would surface as a
compile error in those test files, not as a behavior change.

## 2026-10-23 Package 10 (12B flow acceptance + 12B-10 Room CRUD/isolation) — PENDING CI

Prepared locally; **not accepted** until this package is pushed and all four
workflows are green on the resulting head. No production code changes and no
defect found: every new test pins behavior the production code already
implements. The audit first established what the tree already proves, module
by module, so this package adds only the missing legs instead of duplicating
coverage.

### What already existed (cited, not rewritten)

- **Compose create/edit round trip, all six modules** (androidTest):
  `createAndEditRecordOffline` in the Inventory, Parties, Sales, Purchases
  and Accounts screen tests — create, enter edit, cancel edit (asserts no
  extra save), re-enter edit, save (asserts exactly 2 saves and still 1
  record). Items had the same legs split across `createEditAndConfirmDelete`,
  which entered edit mode but never completed the edit-save (closed below).
  Empty-state and save-error legs per module are Package 5 (12B-08 accepted).
- **ViewModel delegation, all six modules**: each `*ViewModelTest` proves
  save/list/observe hit the scoped repository with the module's own table
  (products, stock_movements, customers, orders, purchase_orders, accounts)
  and the exact scope. Parties also proves delete delegation (12B-04).
- **Room create/edit persistence**: `OperationalBusinessRoomIntegrationTest`
  — `offlineWriteSurvivesDatabaseRestartAndQueuesUpload` (create + CREATE
  op + restart + per-device queue isolation),
  `offlineItemEditPreservesFieldsAndQueuesUpdateAcrossRestart` (edit keeps
  untouched fields, queues CREATE then UPDATE, survives restart),
  `allOperationalModulesPersistOfflineEditsAcrossRestart` (all six module
  tables create+edit across restart, business isolation, 6 CREATE + 6
  UPDATE ops), `operationalTablesRemainIsolatedAcrossTenantsAndBusinesses`
  (same id in three scopes stays separate, per-scope queues),
  `localDeletePersistsTombstoneAndQueueAcrossRestart` (tombstone hides the
  record, survives restart, CREATE+DELETE queued; accounts delete sampled
  for the reversal rejection).

### Per-task verdicts

| Task | Module / scope | Already covered by | New in this package | Residual |
| --- | --- | --- | --- | --- |
| 12B-02 | Items (products) | Compose create + edit-entry + delete confirm; VM delegation; Room create/edit/restart | Compose `editSaveUpdatesExistingRecord` completes the edit-save (renames the record, still 1 record); VM `deleteUsesScopedProductsRepository` (delete delegation was untested for Items); VM `saveFailurePropagatesToCaller` | None known |
| 12B-03 | Inventory (stock_movements) | Compose `createAndEditRecordOffline`; VM delegation; Room six-module create/edit | VM `saveFailurePropagatesToCaller`; Room: stock_movements included in the all-financial delete-rejection sweep and append-only semantics pinned | None known |
| 12B-05 | Sales (orders) | Compose `createAndEditRecordOffline`; VM delegation; Room six-module create/edit | VM `saveFailurePropagatesToCaller`; Room: orders in the delete-rejection sweep | None known |
| 12B-06 | Purchases (purchase_orders) | Compose `createAndEditRecordOffline`; VM delegation; Room six-module create/edit | VM `saveFailurePropagatesToCaller`; Room: purchase_orders in the delete-rejection sweep | None known |
| 12B-07 | Accounts (accounts) | Compose `createAndEditRecordOffline`; VM delegation; Room six-module create/edit; accounts delete sampled for reversal rejection | VM `saveFailurePropagatesToCaller`; Room `financialCorrectionIsACompensatingEntryNotADelete` (correction = second compensating CREATE referencing the original; both entries stay listed; no DELETE ever queued) | None known |
| 12B-10 | Room CRUD + tenant/business isolation (all tables) | Integration tests cited above (create/edit/restart/isolation/tombstone) | New `OperationalCrudGuardRoomTest` (7 tests): observe Flow reflects create/edit/delete and stays scope-empty for another business; save guards reject uuid mismatch, tombstone payloads, unsupported tables, blank tenant and negative revision while staging nothing (no record, no queued op); delete rejection swept across orders/purchase_orders/accounts/stock_movements with records kept; delete requires an existing record in the caller's scope (missing id and cross-scope id both fail, nothing queued); delete in one scope leaves the same id in another scope listed with its own queue untouched; compensating-entry reversal; save-after-delete restores the record and queues CREATE, DELETE, UPDATE in order (pinned as the current semantics of re-creating a deleted master record) | None known |

### Notes

- The save-failure ViewModel tests pin a contract the screens rely on: the
  ViewModel must not swallow a repository failure, because the screen's
  error state (Package 5) is driven by the exception reaching its catch
  block. Previously only the screen-level catch was tested, with the fake
  below the ViewModel.
- Delete remains intentionally absent from the four financial/inventory
  tables at every level: the ViewModels for Inventory, Sales, Purchases
  and Accounts expose no delete at all, and the repository rejects it with
  "Financial and inventory history requires a reversal, not deletion".
  This package asserts that contract per table instead of sampling only
  accounts.
- Escaped-quote check: 0 occurrences of the sequence in all seven new or
  modified Kotlin files.

### Local evidence (this preparation run, 2026-10-23)

Node suites unchanged and green — `server/test_sync_push.mjs` 14/14,
`scripts/test-desktop-sync.mjs` 30/30, `scripts/test-desktop-sqlite.mjs`
7/7. Android cannot compile or run locally (no SDK in this environment),
so the new tests are verified only when CI runs them (feature-module unit
tests and core:store tests via Native Android CI step 15.3 and the
Segmented jobs; the Items Compose test via the Compose UI Emulator
workflow and instrumentation compilation).

## 2026-10-23 Packages 8 + 9 verification (head `dfa5d11d`) — 12C-05 + 12C-08 accepted; both defects fixed

**All four workflows green** — Native Android CI (39843729713), Phase 12
Segmented Verification (39843729651), Compose UI Emulator (39843729654),
Desktop/VPS (39843729604). The road: `726737c9` stored the five Package 8
test files base64-encoded (write-route content error, since corrected —
all routes take raw content, verified by probe and read-back); `08705b6f`
then failed compilation on four `.entityId` mappings over
`OperationalRecord` (whose property is `id`); `dfa5d11d` fixed those and
everything ran green.

- **12C-05 ACCEPTED.** The two-device simulation runs end-to-end in CI:
  the push-settlement defect (own echo tripping the conflict guard) is
  FIXED by the same-response-ack exemption in `SyncEngine` /
  `RoomSyncQueueStore`; a rejected push still conflicts (regression pin);
  delete rounds settle and hide the record on both devices. The (b) policy
  matrix is fully pinned: pending edit refuses remote; already-synced
  editable record is overwritten on arrival (arrival-order-wins);
  already-synced insert-only posting keeps local. Whether
  arrival-order-wins is the intended policy for synced editable records
  remains an explicit OWNER DECISION recorded here and in issue #19; the
  behavior is pinned by tests either way.
- **12C-08 ACCEPTED.** Cross-scope run isolation and switch-then-operate
  are proven in CI; no JVM-provable residual remains.
- **12C-06 PARTIAL.** Proven in CI: corrupt credential bytes fail safe
  (isPaired false, scope stays LOCAL_ONLY, worker run fails at the
  credential check with consent on); the 401 path stops before pull; the
  token appears on exactly one surface (the Authorization header) across
  success/401/missing-token/consent-revoked exchanges. Residuals needing a
  production seam or an instrumented test: the `isPaired` device-mismatch
  true-branch and re-pair-cancels-previous-scope (the pairing manager
  constructs its credential store internally and Robolectric has no
  AndroidKeyStore provider).
- **Tombstone visibility defect FIXED** (found in Package 8, fixed in
  Package 9): pulled soft-delete rows are converted to staged tombstones
  in `RoomSyncQueueStore`, so records deleted on another device no longer
  stay visible in `list()`/`observe()` — proven by
  `PulledTombstoneVisibilityTest` in CI.

Accepted total is now **10 of 22**: 12B-04, 12B-08, 12B-11, 12C-01,
12C-02, 12C-03, 12C-05, 12C-07, 12C-08, 12C-10. Partial: 12B-09
(emulator wiring), 12C-04 (process-kill residual), 12C-06 (Keystore-seam
residuals), 12C-09 (Tauri on-device residual).
