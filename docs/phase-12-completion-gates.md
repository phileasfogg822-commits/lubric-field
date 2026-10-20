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

