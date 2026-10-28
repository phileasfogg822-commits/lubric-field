# Phase 32 — Storefront UI + Demo + Print Pack

Branch `phase-32-ui-demo-print`, from merge head `9016280` (PR #40 merged).
Base: `phase-30-cloud-platform`. PR stays draft; no merge without Umar.

Pins: GitHub-only development holds (no live backend/VPS/endpoints).
Demo data is clearly labelled DEMO and deletable in bulk; it never mixes
with the user's own businesses. Print templates build on the Phase 25
document/print base. Tax uses the existing tax breakdown, not India GST.
Camera capture is out of scope; customer photos use avatar placeholders
plus the existing photo-reference field.

## Package 32.1 — Persistent shell

ACCEPTED at `8f6dffe`: Native 40749227409 success, Compose 40749227666
success, Segmented 40749227350 success (verified via watch + runs API).
6 new JVM tests (BottomBarSelectionTest); existing dashboard/More
suite green. Scaffold hoisted so the bar wraps dashboard, More list,
feature screens and access control; open-module highlight via a pure
helper; FAB dashboard-only.

## Package 32.2 — Khata-style parties

Code at `0af5167` (14 files, +435/−29): photoUri field + codec + form,
initials/photo avatar, live ledger balances via shell balanceFor,
summary card, search, clickable rows, profile current-balance line.
Tests: 11 new JVM (live-balance combine + initials), photoUri codec
round-trip, 2 new compose tests (search filter, initials avatar).
Verdict pending on the 32.2 head's trio.

## Package 32.3 — Demo companies

## Package 32.4 — Calendar date pickers

## Package 32.5 — Print templates

## Package 32.6 — Gate
