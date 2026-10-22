# Phase 12B/12C — bounded execution batches

Branch: `phase-12-completion`. Tracking: Issue #19. PR #12 remains draft/unmerged.

## Operating rules
- A scheduled hourly invocation is a checkpoint, not a continuously running agent. Within each invocation, process several **sequential** safe substeps as time and tool permissions allow; never schedule one tiny edit per hour by design.
- Read current branch head, check previous CI, and identify the first unverified task.
- For each substep: inspect relevant code and tests; implement a focused change; commit to this branch; verify the commit's own GitHub Actions result before marking accepted. If runs are still pending, leave acceptance pending and continue only with independent low-risk work.
- Never bypass failing checks, overwrite unrelated changes, force-push, merge, or release. Report blockers with exact error details. Avoid creating excessive CI runs for documentation-only edits.
- Keep all work free of paid API dependencies; GitHub Actions is verification, not an autonomous coding agent.

## Batch A — Android Items
A1 verify scoped repository and existing tests; A2 implement safe edit; A3 design DELETE semantics compatible with backend sync and offline tombstones; A4 implement delete only after contract is established; A5 add repository/Room restart tests; A6 add ViewModel/UI tests; A7 verify CI and update Issue #19. Edit is implemented at `57f1607`; deletion and acceptance tests remain.

## Batch B — Other five operational modules
B1 inventory; B2 parties; B3 sales; B4 purchases; B5 accounts; B6 navigation, back stack, error/loading/empty states, localization; B7 UI and Room isolation tests; B8 Android lint/tests/debug/release/white-label artifacts.

## Batch C — Durable synchronization
C1 schema/cursor audit; C2 receipt validation/idempotence; C3 retry-after and restart; C4 interrupted push/pull and rollback; C5 concurrent-device conflict policy and financial records.

## Batch D — Security and desktop parity
D1 pairing/revoke/corrupt token; D2 cloud opt-out race and zero-network checks; D3 tenant/business/device isolation; D4 desktop SQLite outbox/cursor parity; D5 Android↔VPS↔desktop simulated replay tests.

## Batch E — Candidate acceptance
E1 run relevant full CI on same candidate SHA; E2 inspect tests, lint and APK artifacts; E3 reconcile completion gates and Issue #19; E4 publish acceptance/known-limitations report. Do not merge without explicit user authorization.

## Checkpoint notes
2026-10-20: Latest verified code commit `57f1607` passed Native Android CI (run 39303553575) and Phase 12 Segmented Verification (run 39303553543). Item edit exists; full CRUD is not accepted. Issue #19 has been updated with evidence.
