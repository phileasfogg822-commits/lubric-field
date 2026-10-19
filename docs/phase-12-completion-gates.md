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
