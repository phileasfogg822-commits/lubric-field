# Phase 24 execution gates — Reporting / Reconciliation / Period Controls

Branch `phase-24-reporting-reconciliation`, base
`phase-23-purchasing-payables` @ `6c94a34`. Packages land on this
branch one at a time, each accepted only with green CI
on its exact head. This doc is the ledger; the design
detail lives in `agent_notes/phase-24-prep.md` (goal workspace,
to be written from the roadmap and the master spec
before 24.1).

## Scope

Reporting, reconciliation, and period controls over
the ledger built in Phases 17–23: trial balance and
financial reports over journal entries, reconciliation
of party balances against ledger effects, and period
open/close controls. The trial balance (Phase 20) and
the party ledger balances (Phases 22–23) are the
existing foundations; Phase 24 turns them into
reports and controls, it does not re-derive them.

## Status

NOT STARTED. Branch + scaffold pushed 2026-10-25;
draft PR #33 awaits the owner's tap. Package
breakdown is settled from the roadmap and the
master spec at planning time — nothing below is a
package commitment yet.

## Package 24.1 — Trial Balance report

Status: ACCEPTED on `4e16d34` (2026-10-25). Trio green:
Native 40197124538, Compose 40197124625, Segmented
40197124699. 1 new file, 2 modified, no build-file
changes, 4 new tests (all in `TrialBalanceReportTest`).

Contents: `trialBalanceForPeriod` in feature/accounts
filters journal entries to an inclusive ISO date range
and delegates to the existing `trialBalance`, so the
report derives from posted entries only (§73).
`loadTrialBalance` on AccountsViewModel wires it to
the ledger surfaces. No UI change: the screen already
renders the preview; report screens land in a later
package. An inverted range throws rather than silently
reporting empty.
