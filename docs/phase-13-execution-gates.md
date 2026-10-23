# Phase 13 — App Shell / Module Registry (Roadmap V2)

Branch: to be created from the Phase 12 candidate when Phase 12 closes
(`phase-12-completion` stays draft/unmerged until the §12.7 gate and the
owner's release authorization). This document is the evidence ledger for
Phase 13, modeled on `docs/phase-12-completion-gates.md`: nothing is
marked accepted without green-CI evidence on the pushed head.

## Authoritative scope

Roadmap V2 names Phase 13 "App Shell / Module Registry". No repo file
enumerates Phase 13 deliverables beyond that title, so the scope is read
from the master spec (`white_label_business_app_master_spec.md`), whose
architecture the phase formalizes:

- **§2 Core Architectural Rule** — do not build "Business App -> Fixed
  Screens -> Fixed Functions"; build "Platform Core -> Tenant / Business
  -> Configuration Engine -> Enabled Modules -> Feature Flags -> Theme /
  Branding -> Permissions -> Business Data". "The application should be
  configuration-driven."
- **§3 / §4 White label, two levels** — deployment configuration
  (Level A) controls "Included modules"; customer configuration
  (Level B, inside the app) controls "Modules". The app shell is where
  both levels become visible.
- **§11 Dashboard** — "Dashboard cards must be configurable."
- **§12 Home Quick Actions** — "Visible actions should be configurable
  per role and business."
- **§13 Main Navigation** — recommended bottom navigation
  (Home / Transactions / Items / Parties / More) with a floating action
  button.

## Gap analysis (verified against the tree, 2026-10-23)

Already present (do not rebuild):

- **Configuration engine.** `whitelist/config`:
  `DeploymentConfig.includedModules` (Level A, fed from
  `white-label/deployment.properties` via `DEPLOYMENT_INCLUDED_MODULES`
  and `AppDeploymentConfigModule`), `BusinessConfig.moduleOverrides`
  (Level B), and `WhiteLabelConfigResolver` producing
  `ResolvedWhiteLabelConfig.enabledModules` (included minus
  business-disabled).
- **Feature-flag facade.** `whitelist/featureflags`:
  `FeatureFlagService` exposes the resolved enabled-module set;
  `FeatureFlagKeys` defines 26 keys (inventory, orders, purchases, pays,
  accounting, reports, users, sync, ...).
- **Capability catalog.** `domain/user` `Capabilities`: dotted capability
  keys with definitions (items.view, inventory.view, parties.view,
  sales.view, purchases.view, accounting.view, sync.manage, ...),
  enforced deny-by-default by the access-control engine below the shell.

Missing (the Phase 13 work):

1. **No module registry.** `MainNavigation` hardcodes the module list
   (a `listOf` of id/label pairs) and the feature dispatch (a `when` over
   raw string ids "items", "inventory", "parties", "sales", "purchases",
   "accounts", "cloudsync"). Nothing else in the tree can enumerate the
   shell's modules, their order, labels, or gating metadata.
2. **The shell ignores the configuration engine.** No consumer outside
   `whitelist/*` reads `enabledModules` / `FeatureFlagService`:
   `MainNavigation` shows every module unconditionally, so Level A
   "Included modules" and Level B module overrides have no visible
   effect in the app shell.
3. **Key-space mismatch.** Navigation ids and configuration keys are
   different vocabularies: items and parties have no configuration key
   at all; sales corresponds to the configuration key "orders";
   accounts to "accounting"; cloudsync to "sync". Any future shell
   gating must go through an explicit mapping, not string equality.
4. **Capabilities stop below the shell.** Modules declare no view
   capability in one place; per-module capability gating of navigation
   (spec §12's "configurable per role") has no shell hook.
5. **Spec §13 shape not adopted.** The shell is a dashboard plus a
   vertical module-button list, not the recommended bottom navigation.
   Adopting that shape is a UX change for a later package, not part of
   the registry foundation.

## Package 13.1 — module registry + registry-driven shell wiring — PENDING CI

Smallest foundational slice; **no end-user behavior change**.

Delivered locally (not yet pushed):

- **Created** `android/app/src/main/java/com/lubrcapp/lubrcapp/shell/AppModuleRegistry.kt`
  - `AppModule` model: stable `id` (in the state-based shell the id is
    both registry key and route), `titleRes` label, `configKey` (the
    module's key in the configuration key space, or null when the
    configuration engine cannot address the module), `viewCapability`
    (the access-control capability that views the module, or null),
    `selectable` (user-openable from the module list), and `content`
    (the composable destination for a resolved `OperationalScope`).
  - Registry entries for all eight shell modules in the pre-registry
    shell order: dashboard (registered as the home surface,
    `selectable = false`, no content — the shell renders it with
    dashboard state and callbacks), items, inventory, parties, sales,
    purchases, accounts, cloud sync. Config-key mapping per gap 3:
    inventory -> "inventory", sales -> "orders",
    purchases -> "purchases", accounts -> "accounting",
    cloud sync -> "sync"; items and parties declare a null config key
    because the configuration key space has no key for them, so
    configuration cannot disable them today (mirroring
    `WhiteLabelConfigResolver`, where overrides can only disable keys a
    deployment included).
  - `visibleModules(enabledConfigKeys = null)`: visibility is data.
    With no configuration supplied (what the shell passes today) every
    selectable module is visible in registry order — identical to the
    pre-registry shell. When a set is supplied, a module is hidden only
    when its declared config key is absent from the set.
  - No icon metadata: the current shell renders text-only module
    buttons and no per-module icon resources exist; the registry can
    grow an icon field when the shell gains iconography (spec §13).
- **Modified** `android/app/src/main/java/com/lubrcapp/lubrcapp/MainNavigation.kt`
  - The module button list now iterates
    `AppModuleRegistry.visibleModules()` (id + title from the registry).
  - Feature dispatch now resolves the selected id through the registry
    and invokes the entry's content, replacing the hardcoded `when`;
    an unknown id renders an empty surface defensively (selections can
    only originate from the registry-driven list; Back still returns to
    the dashboard). Bootstrap states, business switching, scope
    resolution, back handling, and the access-control surface are
    untouched.
- **Created** `android/app/src/test/java/com/lubrcapp/lubrcapp/shell/AppModuleRegistryTest.kt`
  (plain JUnit, mirroring `ThemeConfigMapperTest`): registry contents
  and exact shell order; unique non-blank ids; titles present and
  content present for every selectable module; dashboard registered as
  non-selectable home; null/unknown ids resolve to no module; default
  visibility equals the pre-registry list; config visibility hides only
  gated modules whose key is absent (items/parties stay visible);
  every declared config key exists in `FeatureFlagKeys.ALL`; every
  declared capability exists in `Capabilities.DEFINITIONS`.
- `MainNavigationComposeTest` expectations are unchanged: same labels,
  same order, same bootstrap-state routing — the registry refactor must
  pass it unmodified.

Deliberately NOT in this package (later Phase 13 packages):

- **Live configuration gating of the shell** — passing the resolved
  `enabledModules` into `visibleModules(...)` (needs the shell to
  observe `WhiteLabelConfigService`/`FeatureFlagService` per current
  business; it changes which buttons a given deployment/business sees,
  so it needs its own acceptance evidence).
- **Capability-gated navigation** — hiding modules the current user
  cannot view (spec §12 role configurability).
- **Spec §13 navigation shape** — bottom navigation + FAB replacing the
  dashboard button list.
- **Configurable dashboard cards / quick actions** (spec §11 / §12).
- A dedicated `:core:registry` (or `:app-shell`) Gradle module — the
  registry lives in the app module for this slice because only the app
  module may reference every feature screen; extraction is possible
  once a second consumer exists.

Local evidence:

- Node suites untouched and green: `server/test_sync_push.mjs` 14/14,
  `scripts/test-desktop-sync.mjs` 30/30,
  `scripts/test-desktop-sqlite.mjs` 7/7.
- New/modified Kotlin files carry no escaped-quote literals
  (`grep -c '\\"'` = 0 on all three).
- Android cannot compile locally (no SDK in this environment); first
  verification is CI (app unit tests via Native Android CI `./gradlew
  test`, lint, and the Compose UI emulator workflow for
  `MainNavigationComposeTest`).

CI risk on first run:

- The registry stores composable lambdas in an `object`; if the Compose
  compiler or lint objects to the stored-lambda shape, the fallback is
  an `AppModule` interface with a `@Composable` render function — same
  registry contract, no call-site change beyond the model.
- Lint previously failed a build on a real error (Package 5b history),
  so an unused-import or API-surface lint finding in the touched files
  is the most likely first-round failure; imports in `MainNavigation`
  were pruned to match the new wiring.

## 2026-10-23 Package 13.1 verification (head `bdf7e383`) — registry foundation accepted

**All three applicable workflows green on the first run** — Native Android
CI (39853002790), Phase 12 Segmented Verification (39853002681), Compose
UI Emulator (39853002619), all via PR #22. (Desktop/VPS does not trigger
on this branch — see the infrastructure note in this doc's scope section;
13.1 touches no server/desktop code and the node suites were verified
locally: 14/14, 30/30, 7/7.)

- `AppModuleRegistry` is in the tree with the eight shell surfaces in
  their exact legacy order; the 9 registry unit tests pass.
- `MainNavigationComposeTest` passed **unmodified** — the
  behavior-preservation proof for the registry rewiring (same labels,
  order, routing, bootstrap states).
- Accepted as the Phase 13 foundation. Next: Package 13.2 — live
  configuration gating (feed the resolved `enabledModules` into the
  registry's visibility), the first package that changes what the shell
  shows, with its own acceptance evidence.

## Package 13.2 — live configuration gating of the shell — VERIFIED

**Verified 2026-10-23 on head `53a27b5a` — all three workflows green:** Native Android CI 39856769786, Phase 12 Segmented Verification 39856769719, Compose UI Emulator 39856770030. The shell now honors the resolved deployment+business module configuration; bootstrap/no-business behavior is unchanged (all modules visible). Accepted.

First behavior-changing Phase 13 package: the shell now consumes the
configuration engine it previously ignored (gap 2). Which module buttons
a deployment/business sees is decided by the resolved white-label
configuration, through the registry's visibility data from 13.1.

Delivered locally (not yet pushed):

- **Created** `android/app/src/main/java/com/lubrcapp/lubrcapp/ShellConfigViewModel.kt`
  - A thin `@HiltViewModel` window on the production resolution path:
    `FeatureFlagService.observeEnabledFeatures(tenantId, businessId)`,
    whose implementation (`ResolvedFeatureFlagService`) maps
    `WhiteLabelConfigService.observeResolvedConfig` to its
    `enabledModules`. That service is Hilt-bound
    (`WhiteLabelConfigModule` -> `WhiteLabelConfigServiceImpl`) and
    resolves the deployment from `AppDeploymentConfigModule`'s
    `DeploymentConfigSource` (BuildConfig generated from
    `white-label/deployment.properties`, including
    `DEPLOYMENT_INCLUDED_MODULES`) layered with the persisted business,
    settings and feature-flag repositories through
    `BusinessConfigurationParser` and `WhiteLabelConfigResolver`. No
    parallel config system was built; the shell reads the same resolved
    configuration everything else uses.
  - `enabledModuleKeys(context)` returns `Flow<Set<String>?>`: the
    resolved key set, or null when resolution fails (the config service
    errors while the business is not yet visible to its repositories).
    Null is the shell's "no gating" value, so a configuration problem
    can never hide a module.
- **Modified** `android/app/src/main/java/com/lubrcapp/lubrcapp/MainNavigation.kt`
  - New `shellConfigViewModel: ShellConfigViewModel = hiltViewModel()`
    parameter (existing callers and tests that pass the other view
    models explicitly are unaffected in shape).
  - In the Ready state, a `produceState` keyed on the current
    `TenantContext` collects the view model's flow — the same pattern
    the shell already uses for operational-scope resolution. The module
    button list now calls
    `AppModuleRegistry.visibleModules(enabledModuleKeys)` instead of
    `visibleModules()`.
  - **Bootstrap / no-business behavior is unchanged.** While bootstrap
    is Loading / NeedsSetup / Error, and whenever there is no current
    business context yet, the key set is null and every selectable
    module is visible — byte-for-byte the pre-13.2 shell. The module
    list is only rendered with a current context anyway.
  - **Business switching re-resolves.** The `produceState` is keyed on
    the current context: a switch cancels the old collection, resets
    the key set to null (all visible, as today), and collects the newly
    selected business's resolved set, so the visible buttons follow the
    new business's overrides. Dashboard switching already resets the
    selected feature, so no route into a newly hidden module dangles.
  - Dispatch is not visibility-gated: registry content resolution by id
    is unchanged, matching 13.1.

Tests:

- **Created** `android/app/src/test/java/com/lubrcapp/lubrcapp/ShellConfigViewModelTest.kt`
  (plain JUnit, fake `FeatureFlagService`): forwards the resolved set
  for exactly the requested tenant/business (two businesses resolve
  differently — the per-business query is recorded); live configuration
  updates flow through in order; a resolution failure emits null (no
  gating); a resolved set composed with the real registry hides the
  gated modules it excludes.
- **Created** `android/app/src/test/java/com/lubrcapp/lubrcapp/shell/ResolvedShellVisibilityTest.kt`
  (plain JUnit): the real `WhiteLabelConfigResolver` composed with the
  real `AppModuleRegistry` — no double between configuration and
  visibility. Deployment excluding gated modules hides them (Level A);
  a business override `false` disables an included module (Level B); an
  override `true` keeps an included module but cannot enable a
  deployment-excluded one (pins the resolver contract at shell level);
  unknown configuration keys (reports, future keys) are ignored;
  items/parties survive an empty resolved set; the shipped
  `deployment.properties` module set keeps every module visible.
- **Modified** `android/app/src/androidTest/java/com/lubrcapp/lubrcapp/MainNavigationComposeTest.kt`
  — the harness already builds every view model on fakes, so it takes a
  fake `FeatureFlagService` (default: the shipped deployment's gated key
  set) behind a real `ShellConfigViewModel`. The four pre-existing
  tests are expectation-identical — with the default resolved
  configuration the ready dashboard still shows all seven module
  buttons. New test
  `readyBootstrapHidesModulesExcludedByConfiguration`: a resolved set
  of {inventory, orders, accounting} shows Items / Inventory /
  Customers / Sales / Accounts and (awaited) no Purchases or Cloud sync
  button. The dashboard itself renders no such labels (verified against
  `feature/dashboard`), so absence assertions bind only the shell list.

Spec-fidelity note — the items/parties key space (flagged, not fixed):

- Items and parties declare a null registry `configKey` because
  `FeatureFlagKeys` has no key for them, so after 13.2 they remain
  visible for every deployment and business: the configuration engine
  cannot address them, exactly as the resolver already treated
  non-included keys. This package documents the consequence rather than
  extending `FeatureFlagKeys` — adding keys (e.g. "items", "parties")
  is a configuration-schema change that also touches deployment files
  and the key-space contract tests, and the master spec does not
  demand it for this slice. It stays a named follow-up: deployment
  authors must not expect `includedModules` to gate core master-data
  modules today.

Deliberately NOT in this package: capability-gated navigation (spec
§12 role configurability), the §13 bottom-navigation shape,
configurable dashboard cards / quick actions (§11 / §12), and any
change to the resolver, the parser, or the configuration key space.

Local evidence:

- Node suites untouched and green: `server/test_sync_push.mjs` 14/14,
  `scripts/test-desktop-sync.mjs` 30/30,
  `scripts/test-desktop-sqlite.mjs` 7/7 (PASS counts re-verified, 0
  FAIL).
- New/modified Kotlin files carry no escaped-quote literals
  (`grep -c '\"'` = 0 on all five).
- Android cannot compile locally (no SDK in this environment); first
  verification is CI (app unit tests + lint via Native Android CI, the
  Segmented matrix, and the Compose UI emulator workflow for
  `MainNavigationComposeTest`).

CI risks on first run:

- `MainNavigationComposeTest` runs on the emulator workflow only when
  `:app` connected tests are wired (the standing 12B-09 workflow note);
  if it still does not execute there, the gated-variant evidence lands
  with that wiring, while the unit tests above carry the package's
  proof in Native/Segmented either way.
- The gated compose test awaits absence with the same `waitUntil`
  pattern the file already uses for presence; if emulator timing makes
  absence flaky, the fallback is asserting the visible set after the
  gated labels appear rather than racing the ungated first frame.
- Lint: the new view model and the touched navigation file follow the
  file's existing import and constructor patterns (view models are
  built outside `setContent` in tests); a lint finding in the new
  files remains the most likely first-round failure, as with 13.1.

## Package 13.3 — capability-gated navigation — VERIFIED

**Verified 2026-10-23 on head `6da576a2` — all four workflows green:** Native Android CI 39864205566, Phase 12 Segmented Verification 39864205400, Compose UI Emulator Tests 39864205548, Desktop and VPS Verification 39864205467. Verification took three runs, each documented honestly: the first wired run (`090d2adc`) died before Gradle started — GitHub's runner failed installing the Android Emulator SDK package ("Error on ZipFile unknown archive", pure infrastructure); the tree-identical re-trigger (`24d53883`) ran the tests for the first time and **5 of 7 `MainNavigationComposeTest` tests failed** with `IllegalStateException: DesignTokens are only available inside LubrcappTheme` — the harness mounted `MainNavigation` bare while production hosts it inside `LubrcappTheme` (`MainActivity`), the sole provider of `LocalDesignTokens`. Fix `6da576a2` wraps the test content in `LubrcappTheme` with a neutral `DesignSystemConfig` (test-only change; production code untouched), after which all 7 instrumented tests execute on the emulator and pass, alongside the two new unit suites. The deny-by-default capability gate is therefore proven at unit level AND at UI level on the emulator. **Package 13.3 ACCEPTED.**

What was wired:

- **New** `android/app/src/main/java/com/lubrcapp/lubrcapp/ShellAccessViewModel.kt`
  — a `@HiltViewModel` exposing
  `grantedCapabilities(context: TenantContext): Flow<Set<CapabilityKey>>`:
  it forwards the production `AccessControlRepository`'s authorization
  snapshot for the context, reduced to the granted capability set. It
  mirrors `ShellConfigViewModel`'s shape (13.2) but not its failure
  semantics — see the deny-by-default decision below.
- **Modified** `android/app/src/main/java/com/lubrcapp/lubrcapp/shell/AppModuleRegistry.kt`
  — `visibleModules` gains a second gate parameter
  (`grantedCapabilities: Set<CapabilityKey>? = null`) and delegates to a
  new public predicate `isVisible(module, enabledConfigKeys,
  grantedCapabilities)`: a module passes only when the configuration gate
  passes it (13.2 semantics unchanged) AND the capability gate passes it
  (its `viewCapability` is null, or the caller supplied no grant set, or
  the grant set contains the capability). Registry defaults keep every
  13.1/13.2 call site and test behaving identically.
- **Modified** `android/app/src/main/java/com/lubrcapp/lubrcapp/MainNavigation.kt`
  — new trailing parameter
  `shellAccessViewModel: ShellAccessViewModel = hiltViewModel()`; in the
  Ready state a `produceState` keyed on the current `TenantContext` (the
  same pattern as scope resolution and 13.2's configuration state)
  collects the grant set; the shell computes ONE composed
  `visibleModules(enabledModuleKeys, grantedCapabilities)` list used by
  both the button list and feature dispatch. Dispatch now renders a
  selected module's surface only when `isVisible` passes for it, so a
  module hidden by either gate — including one left open while its grant
  was revoked live — renders no surface and falls back to the existing
  empty-surface-plus-Back behavior. The only production caller,
  `MainActivity`, passes just `appName`, so the signature change is
  source-compatible. Bootstrap states, business switching (which already
  reset `selectedFeature`), and the Access Control screen path are
  untouched.

Capability source (production, not newly invented):

- `AccessControlRepository` (core:repository), Hilt-bound in
  core:store's `RepositoryModule` to `RoomAccessControlRepository` —
  the same source the settings Access Control screen already consumes
  through `AuthorizationService`. Its `observeAuthorization` combines
  the membership, role, and permission flows for the context; a user
  with no active membership in the business resolves to a denied
  snapshot (null membership, no roles, no capabilities). The owner's
  role is seeded and repaired to `Capabilities.ALL` by
  `ensureOwnerAccess`, which the view model runs before observing —
  the exact read pattern `AuthorizationService.snapshot()` established.
  No parallel permission system was built, and no business-data access
  changes: this package gates navigation visibility only; the feature
  screens' own guards are untouched.

Deny-by-default decision and its basis:

- Access control's existing contract is deny-by-default:
  `AuthorizationSnapshot.allows` is false without an active membership,
  the repository emits a denied snapshot rather than "unknown" for
  non-members, and the settings screen treats a missing snapshot as not
  allowed (`snapshot?.allows(...) == true`). The shell follows that
  contract rather than 13.2's fail-open configuration behavior:
  - the shell's grant state starts as the empty set and resets to it on
    every context change, so until the snapshot for the newly selected
    business resolves, capability-gated modules do not show — and the
    previous business's grants are never carried across a switch;
  - a resolved snapshot with no active membership emits the empty set
    (the view model re-checks `membershipId` itself, encoding `allows`
    semantics rather than trusting the capability list alone);
  - a resolution failure emits the empty set (the flow's `catch` emits
    empty), so an authorization error hides gated modules instead of
    showing them.
- Deliberate asymmetry, documented rather than hidden: configuration
  gating (13.2) fails OPEN because configuration is a white-label
  concern — a broken config must not brick the shell; capability gating
  fails CLOSED because it is an authorization boundary, matching every
  existing access-control consumer in the tree.
- Consequence worth naming: unlike configuration, capabilities CAN hide
  items and parties — they declare no `configKey` but do declare
  `items.view` / `parties.view`. With an empty grant set every
  selectable module is hidden (all seven declare a view capability);
  in practice the Ready state is only reachable with a validated
  membership (`CurrentBusinessProvider.ensureCurrent` /
  `BusinessSwitchResult.AccessDenied`), and the owner holds all grants,
  so the default shell is unchanged.

Tests:

- **New** `android/app/src/test/java/com/lubrcapp/lubrcapp/shell/CapabilityGatedVisibilityTest.kt`
  (8 plain-JUnit tests, real registry): full config + full grants show
  everything; config-visible but capability-denied hides exactly that
  module; full grants cannot resurrect a config-hidden module; the two
  gates compose per module; an empty grant set hides every selectable
  module; a null grant set leaves the capability gate inert (13.2
  behavior preserved); a module with no view capability (the dashboard)
  is never capability-hidden; the dispatch predicate (`isVisible`)
  rejects capability-hidden and config-hidden modules.
- **New** `android/app/src/test/java/com/lubrcapp/lubrcapp/ShellAccessViewModelTest.kt`
  (5 plain-JUnit tests over a fake `AccessControlRepository`): grants
  forwarded for exactly the requested tenant/business (two businesses
  resolve differently, owner-access and observation calls recorded);
  a snapshot without membership yields no grants even if it names
  capabilities; live authorization updates pass through in order;
  resolution failure falls back to the empty set; the view model's
  grants composed with the real registry hide the right modules.
- **Modified** `android/app/src/androidTest/java/com/lubrcapp/lubrcapp/MainNavigationComposeTest.kt`
  — the harness takes a fake `AccessControlRepository` (default grants:
  `Capabilities.ALL`, the owner's set) behind a real
  `ShellAccessViewModel`; the five pre-existing tests keep identical
  expectations. New tests:
  `readyBootstrapHidesModulesDeniedByCapability` (grants exclude
  `purchases.view` → no Purchases button, the other six remain) and
  `capabilityRevocationHidesModuleLive` (grants change under the running
  shell → the Purchases button disappears without a restart or switch).

Local evidence:

- Node suites untouched and green: `server/test_sync_push.mjs` 14/14,
  `scripts/test-desktop-sync.mjs` 30/30,
  `scripts/test-desktop-sqlite.mjs` 7/7 (PASS counts re-verified, 0
  FAIL).
- New/modified Kotlin files carry no escaped-quote literals
  (`grep -c '\\"'` = 0 on all six).
- Android cannot compile locally (no SDK in this environment); first
  verification is CI (app unit tests + lint via Native Android CI, the
  Segmented matrix, and the Compose UI emulator workflow for
  `MainNavigationComposeTest`).

CI risks on first run:

- `MainNavigationComposeTest` executes on the emulator workflow only
  when `:app` connected tests are wired (the standing 12B-09 workflow
  note); if it still does not execute there, the unit tests above carry
  the package's proof in Native/Segmented either way.
- Deny-by-default changes the shell's first frame: until the
  authorization snapshot resolves, the module list is empty where 13.2
  briefly showed every module. The Compose harness's await-presence /
  await-absence helpers absorb this in tests, and on device the Room
  flows resolve from the local database; a lint or timing surprise here
  is the most likely first-round failure, as with 13.1/13.2.
- The dispatch guard shares the composed predicate with the list (one
  code path, pinned by `CapabilityGatedVisibilityTest`), but no Compose
  test clicks through into a feature surface — that still needs the
  Hilt-graph harness noted under 12B-09, so the guard's UI-level proof
  lands with that wiring while its predicate proof is in unit tests now.

## Package 13.4 — bottom-navigation shape — VERIFIED

What was wired:

- **New** `BottomNavSlot` enum + `bottomNavSlot` field on `AppModule` in
  `AppModuleRegistry`: `dashboard → HOME`, `items → ITEMS`, `parties →
  PARTIES`. The spec's Transactions slot is deliberately absent — there is
  no transactions module or screen in the tree (it is spec §14, a later
  phase); when its registry entry lands with a slot, the bar picks it up
  with no shell change.
- **New** `AppModuleRegistry.bottomNavModules(...)`: slotted modules in
  slot order, each still passing the full `isVisible` gates — a module
  hidden by configuration or capability never reaches the bar.
- **New** `ShellBottomBar` composable: Material 3 `NavigationBar`
  (Home / Items / Customers / More) plus the overflow "More" destination,
  which is a shell surface, not a registry module.
- **Modified** `MainNavigation`: the ready surface is now a `Scaffold`
  with the bar; "More" swaps the dashboard for the full module list
  (unchanged labels and behavior); a bar tap sets the same
  `selectedFeature` the list sets, so the existing dispatch branch guards
  it with the same composed visibility. System Back now also unwinds
  "More"; business switch resets it.
- **Not wired:** the spec's floating action button "+". No create-flows
  exist yet (§12 quick actions are Package 13.5), and a dead "+" would be
  worse than none — the FAB slot arrives with 13.5, where it opens the
  quick-actions sheet.

Tests:

- **New** `BottomNavSlotTest` (10 plain-JUnit tests, real registry):
  exact slot assignments; only Home/Items/Parties hold slots; slotted
  destinations are selectable; slot order; capability gate removes
  slotted modules from the bar (empty grants leave only Home); and the
  parity pin — under every gate mix, the bar's destinations stay within
  the composed-visible set, so the bar cannot open a gated module.
- **Modified** `MainNavigationComposeTest` (:app androidTest): the ready
  surface now asserts the bar (Home/Items/Customers/More) instead of the
  list; the module-list assertions moved under a tap on More; the
  config-gated and capability-denied assertions follow; new tests pin
  More→list→Home round-trip and a capability-denied slotted module
  (Customers) disappearing from the bar.
- **Explicit gap:** no Compose test clicks a bar destination into its
  feature surface — feature screens resolve Hilt ViewModels and the
  harness has no Hilt graph (same standing gap as 13.3's dispatch
  guard). The bar↔list dispatch parity is the same `selectedFeature`
  state through the same guarded branch, pinned by `BottomNavSlotTest`
  at unit level; UI-level click-through lands with the Hilt-graph
  harness (the residual noted under 12B-09).

Local evidence:

- New/modified files carry no escaped-quote literals (`grep` = 0 on all
  six). Android cannot compile locally (no SDK in this environment);
  first verification is CI.
- Dependency added: `androidx.compose.material:material-icons-core`
  (BOM-versioned) for the bar icons — the only build-file change.

CI risks on first run:

- `MainNavigationComposeTest` in the More state has two nodes for the
  slotted labels (bar item + list item), so the list assertions cover
  only the non-slotted modules; if the runner's text matching behaves
  differently on a real device, CI will say so.
- The new icons dependency is one line, but a BOM/artifact mismatch
  would fail compilation before any test runs — the Native workflow
  arbitrates.

## Package 13.5 — quick actions, FAB, dashboard cards — VERIFIED

What was wired (commit `63996af`):

- **New** `QuickActionsRegistry`: 8 quick actions (+ Sale, + Purchase,
  + Order, Party, Item, Stock Adjustment, Expense, Income), each targeting
  a real registry module and gated by BOTH the target module's composed
  visibility (13.2 configuration + 13.3 capability) AND its own
  create/manage capability (spec §12). Sales is the orders module, so
  + Sale and + Order share the sales target with distinct capabilities
  as registered. Payment In/Out and the distinct document flows
  (Quotation / Sales Order / Purchase Order / Delivery Challan) stay
  deliberately unregistered until their surfaces exist (Phases 18/21/22/27)
  — no dead ends.
- **New** `QuickActionsSheet`: a quick-actions section on the dashboard
  and the FAB "+" opening a `ModalBottomSheet` with the same visible
  actions. The FAB renders only when at least one action is visible and
  a shell context exists. Taps set the same `selectedFeature` as the
  list and the bottom bar, so the existing dispatch guards apply
  unchanged.
- **New** `DashboardCardsRegistry`: dashboard-card visibility through
  the same two-gate semantics. Ships the one card whose data exists
  today — CURRENT_BUSINESS (ungated) — via a new `showCurrentBusinessCard`
  parameter on `DashboardScreen` (default preserves prior behavior).
  Financial/indicator cards (Today's Sales etc.) are deliberately NOT
  fabricated; they join with their data sources in Phases 17–24.

Tests:

- **New** `QuickActionsRegistryTest` (8 unit tests) and
  `DashboardCardsRegistryTest` (5 unit tests), same gate-mix style as
  13.2–13.4.
- **Modified** `MainNavigationComposeTest`: 3 new tests — section+sheet
  listing counts, capability-denied actions absent, FAB vanishes when
  no action is visible.

## 2026-10-23 verification — Packages 13.4 and 13.5 ACCEPTED

How the evidence was produced (recorded in full because it was not the
normal path): from the morning of 2026-10-23, GitHub's event pipeline
delivered no `pull_request` runs for this branch at all — three pushes
(`ecce138`, `75c21d1`, `63996af`) and a close/reopen of PR #22 produced
zero runs, across ~7 hours, while all six workflows showed active
(GitHub had a multi-day Actions/webhooks event-streaming degradation,
Oct 21–23). All verdicts below are therefore from owner-triggered
`workflow_dispatch` runs on the exact heads named — same code, same
runners, same suites; only the event type differs.

Two enabling changes were needed along the way:

- The branch-only workflow files could not be dispatched at all:
  GitHub shows the Run button only for workflows present on the default
  branch. With the owner's explicit approval, byte-identical copies of
  `phase-12-segments.yml` (blob `ea75ed25`) and
  `compose-ui-emulator.yml` (blob `e7d16266`) were added to `master`
  in `8d56b6d`; neither triggers on push, so the commit started no runs.
- The connector has no Actions tools, so dispatch stayed a manual,
  owner-side action throughout; code and fixes moved via git SSH as
  usual.

Failure → fix sequence:

1. Native on `63996af` (run 39904377673) FAILED at
   `:app:compileDebugKotlin`: `Inventory`, `People`, `MoreHoriz`
   unresolved in `ShellBottomBar.kt`. The 13.4 section above records
   the dependency as added — `material-icons-core`; those three icons
   in fact live in `material-icons-extended` (the designsystem module
   already uses extended). Fixed in `7a1c51c` by swapping the app
   module to `-extended` (a superset of `-core`). No code changes.
2. Native on `7a1c51c` (run 39907540633) — **SUCCESS**.
   Segmented on `7a1c51c` (run 39909039352) — **SUCCESS**.
3. Compose on `7a1c51c` (run 39909010279) FAILED before running any
   test, at `:app:compileDebugAndroidTestKotlin`: 7× unresolved
   `performClick` in `MainNavigationComposeTest.kt` — in this Compose
   test version `performClick` is an extension requiring
   `import androidx.compose.ui.test.performClick`. Fixed in `5f3d113`
   (one import line; androidTest sources only).
4. Compose on `5f3d113` (run 39910759999) — **SUCCESS** (8m 28s,
   emulator suite incl. the updated 13.4 bar tests and the new 13.5
   quick-action/FAB tests).

Evidence note on heads: Native and Segmented verdicts stand on
`7a1c51c`; the only delta to the final head `5f3d113` is the single
androidTest import above, which neither of those workflows compiles
(Native runs JVM unit tests; Segmented runs segment unit tests), and
Compose — the one workflow that compiles and runs the androidTest
sources — is green on `5f3d113` itself. Packages 13.4 and 13.5 are
therefore ACCEPTED on final head `5f3d113`.

Phase 13 status: 5 of 5 packages accepted (13.1 registry, 13.2
configuration gating, 13.3 capability gating, 13.4 bottom navigation,
13.5 quick actions/FAB/dashboard cards). Remaining phase work is the
close-out sweep only; issue/PR text updates go through the connector
with owner approval as before.
