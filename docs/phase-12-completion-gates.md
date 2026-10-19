# Phase 12 — completion gate (2026-10-19)

Branch: `phase-12-completion`; draft PR #12. Do not merge until all gates pass.

## Verified CI evidence
- GitHub commit `fd5cf51f`: CircleCI statuses `phase12-smoke` and `android-full-verification` both reported success.
- CircleCI workflow: https://app.circleci.com/workflow/63af6be8-f9fd-4e9b-b25e-baff6459f3f9
- The job statuses are not a substitute for inspecting individual Gradle steps, test reports, and APK artifacts.
- Legacy GitHub Actions still report failures; do not disable until parity is demonstrated.

## Outstanding acceptance gates (7)
- [ ] **12-A: CI evidence:** Inspect full CircleCI step logs, test XML, lint report, debug/release/white-label APK artifacts; verify distinct APKs and hashes.
- [ ] **12-B: Android UI:** Implement and wire functional Compose screens for items, inventory, parties, sales, purchases and accounts; add automated UI/navigation tests. Repository audit currently finds an ItemsScreen but no corresponding screen files for the other five domains.
- [ ] **12-C: Durable sync:** Add regression tests for restart, idempotent replay, receipt handling, server retry-after, cursor monotonicity, rollback and conflicting local edits.
- [ ] **12-D: Cloud consent and security:** Test mid-flight opt-out, no-network local-only mode, tenant switching, revocation, expired and corrupt credentials, and log redaction.
- [ ] **12-E: Desktop parity:** Verify Tauri SQLite outbox/cursors, schema parity, replay and multi-device conflict contracts against Android/backend.
- [ ] **12-F: Complete cloud acceptance:** Run all applicable tests, lint, instrumentation-test compilation, debug/release and white-label builds on the same candidate commit; preserve reports and artifacts.
- [ ] **12-G: Release review:** Reconcile CI parity, retire duplicate GitHub Actions only after replacement verified, complete PR review and merge only after explicit authorization.

## CI cost policy
Keep the smoke job on pushes. Run expensive full verification intentionally using the CircleCI `run_full_verification=true` parameter; see CircleCI project pipeline trigger settings. If a GitHub App trigger overrides this parameter, validate actual workflow behavior and adjust trigger configuration before assuming cost limits.

## Current active work
12-A: CI evidence and build-artifact verification; 6 additional gates remain after 12-A.
