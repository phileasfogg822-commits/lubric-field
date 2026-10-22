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
