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
