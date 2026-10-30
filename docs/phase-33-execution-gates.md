# Phase 33 — Full UI/UX redesign (phone + tablet, responsive)

Owner verdict on the Phase 32 gate APK (2026-10-30 ~04:14 PKT):
the UI is unchanged — no new graphics, no better display. Demo
companies are present and functionality is good, but the UI is
very basic and hard to understand. Phase 32 changed structure
(bottom bar everywhere, demo data, date pickers, print themes)
but never changed the visual language: the theme is stock
Material3 derived from the white-label palette, with default
typography, default shapes, and no charts or graphics anywhere.
This phase fixes that: a real visual identity, applied app-wide
through the theme so every screen changes at once, plus a
redesigned Home, modern lists and forms, in-app graphics, and
adaptive layouts for phones and tablets.

Base: `922890e` (Phase 32 gate-verdict head). Branch
`phase-33-ui-ux-redesign`, stacked draft PR on base
`phase-32-ui-demo-print` (so this PR's diff is Phase 33 only).
Same rules as Phase 32: a segment is accepted only with green
CI on the exact head; no merges without the owner's approval.

Design direction (decided, not copied from any one app):
- A distinctive modern finance look: deep-teal primary with a
  warm amber accent, clear green/red money semantics (in = green,
  out = red, matching the Khata convention users already know),
  soft neutral surfaces, generous rounded cards (20dp), and a
  tight, readable type scale — not the stock Material purple.
- Home that explains itself at a glance: balance hero, cash
  in/out visual bars, receivables vs payables, quick actions as
  a labelled grid, recent activity — no raw module names as the
  first thing a user sees.
- Lists as modern cards: avatar, name, badge, color-coded
  amount, one-line context — not plain text rows.
- Forms that guide: section cards, clear labels, big touch
  targets, sticky primary action.
- Graphics drawn in-app with Canvas (no new chart library):
  cash-flow bars on Home and a balance visual on statements.
- Responsive by width breakpoint (compact < 600dp = phone
  single column; medium/expanded = tablet two-column grids and
  list-detail side by side), implemented with BoxWithConstraints
  so no new dependency is needed.
- Dark theme polished alongside light, not just inverted.

## Package 33.1 — Design system foundation

Code at `cf27f2e` (7 files, +352/−17): the default brand
palette leaves the stock Material template colors behind —
deep teal primary, bright teal secondary, warm amber accent,
green/red money semantics, 20dp radius — in
white-label/deployment.properties, the single source (no test
or code hardcoded the old hex values; white-label overrides
still win). Light surfaces become white cards on a soft
tinted background; dark surfaces become a deep
charcoal-green pair instead of flat #121212. New adaptive
width classes (compact <600dp, medium <840dp, expanded) as a
pure function for the tablet layouts in 33.5, and shared money
visuals: direction colors, cash-flow bar fractions (inputs
clamped at zero, both zero yields empty bars), and CashFlowBars
(label + amount row, rounded track with a success/error fill;
labels are caller parameters, so no new strings and no locale
work). Contrast checked by hand for the new palette against
the luminance rule (teal-700, amber-600, green-600, red-600
all take white text; amber-500 stays warning-only because it
sits just under the threshold).
Tests: 10 JVM (width boundaries incl. 599/600/839/840, bar
fractions incl. both-zero and negatives clamped) + 2 compose
in :feature:sales androidTest (bars and money text render;
wrapped in LubrcappTheme with a test palette because
LubrcTheme.tokens crashes outside the theme host).
ACCEPTED at `cf27f2e`: Native 40972249988 success, Compose
40972250012 success, Segmented 40972250164 success —
first-try green, including the 2 new money-visuals compose
tests on attempt 1.
Note: this changes colors, surfaces, and cards app-wide, but
no screen layout changes here — Home's new content and
structure land in 33.2.

## Package 33.2 — Home redesign

Home was a bare business-name headline. It now opens with a
primary-colour hero card (business name, type, currency, DEMO
badge), a money-overview card fed by the ledger (receivables
vs payables as cash-flow bars from 33.1), and the quick
actions as a two-column tappable card grid instead of a plain
button list. HomeSummary is pure dashboard data;
HomeSummaryMapping sums signed ledger rows per direction in
the first non-blank currency and stays null (card hidden)
when there are no parties. DashboardScreen gains a defaulted
homeSummary param, so existing callers render exactly as
before; MainNavigation loads the summary when the scope
resolves and the shell ViewModels are present (null
ViewModels in the navigation tests skip loading).
Tests: 5 JVM (signed sums, currency filtering, unknown
directions, empty, blank currency).
First head `9ec4d56` failed both workflows on one compile
error — smart cast on delegated `operationalScope` at
MainNavigation.kt:418 — fixed in `a45d4e2` by capturing it
with `?.let { scope -> }`.
ACCEPTED at `a45d4e2`: Native 40978302750 success, Compose
40978302714 success, Segmented 40978302728 success.

## Package 33.3 — Modern lists

## Package 33.4 — Modern forms

## Package 33.5 — Tablet responsive layouts

## Package 33.6 — Gate
