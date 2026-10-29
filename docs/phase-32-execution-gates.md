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
ACCEPTED at `bf5e78d`: Native 40756403085 success, Segmented
40756401779 success, Compose 40756401878 success on re-run attempt 2
(verified via watch + runs API + jobs API). History: first head
`0af5167`/`49fb941` trio all red on one compile error
(`produceState` null initial value inferred `Nothing?`); fix `db8d275`
went 2/3 (Native + Segmented green, Compose failed on a second compile
error — `assertDoesNotExist` not importable); fix `bf5e78d` went 2/3
with Compose failing once on the known `adjustFlowPostsAMovement`
flake ("Adjustment posted" not displayed; inventory code untouched by
this package, same test as the Phase 31 gate flake), then green on a
failed-jobs-only re-run. Attempt 2 ran the full suite, so both new
parties compose tests are emulator-verified. Residual: the live-balance
summary card itself still has no emulator coverage (harness does not
wire the shell provider) — to be checked on device at the gate APK.

## Package 32.3 — Demo companies

Code at `0bc330c` (11 files, +1002/−2): demo `BusinessEntity` rows +
memberships for the current user (two companies: Demo General Store,
Demo Pharmacy Traders), demo-scoped records across every tab through
the same typed save paths and posting handlers the screens use,
dashboard DEMO badge, delete-demo-companies dialog, and a demo-only
hard wipe in the store (`deleteBusinessScope`, child-first in one
transaction, refused for any non-demo scope). Tests: 2 JVM (prefix +
defs) + 3 Robolectric store tests (wipe, guard on real scope, guard
on mixed scope); store suite now 104 tests. Seeded demo figures:
invoice INV-DEMO-1 4400, return 220, receipt 2000, net Ali
receivable 2180; purchase 24400, supplier payment 10000, net
payable 14400; cash in 5000, cash out 1500.
ACCEPTED at `b7ee326`: Native 40937430864 success, Segmented
40937430859 success, Compose 40937430961 success on re-run attempt 2
(verified via watch + runs API + jobs API). History: first head
`0bc330c` went 1/3 (Segmented green; Native failed at the store
tests on one FK miss — the wipe test seeded a membership with no
`UserEntity` row; Compose failed before tests); fix `4838568` went
1/3 on a KSP error — the three `*LocalDataSource` interfaces were
imported from `core.repository` but their defining file declares
`core.repository.datasource` (subpackage trap, second instance of
the 31.2 import lesson); fix `f94922f` went 2/3 (Segmented +
Compose green; Native and Compose both failed `:app:compileDebugKotlin`
with the same 8 errors — PartyType lives in domain.party, the
PurchasePostingAccounts import was dropped by an edit,
loadPayableForPurchase takes scope first, getOrCreate already
returns DeviceId); fix `ff052c3` went 2/3 (Segmented + Compose
green; Native failed at lint only — the 6 new dashboard strings
were missing from values-ar/hi/ur, MissingTranslation); fix
`b7ee326` went 2/3 with Compose failing once on the known
`adjustFlowPostsAMovement` flake (\"Adjustment posted\" not
displayed; diff vs the green `ff052c3` Compose run is only the
three locale strings files, zero inventory paths — third
occurrence of this flake across heads, green on re-run every
time), then green on a failed-jobs-only re-run. Residuals: the
DEMO badge/delete flow and the live-balance summary card on demo
data are device checks at the gate APK (no emulator harness wires
the shell provider or the Hilt MainNavigation path).

## Package 32.4 — Calendar date pickers

## Package 32.5 — Print templates

## Package 32.6 — Gate
