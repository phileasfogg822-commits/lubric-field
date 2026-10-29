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

## Package 33.2 — Home redesign

## Package 33.3 — Modern lists

## Package 33.4 — Modern forms

## Package 33.5 — Tablet responsive layouts

## Package 33.6 — Gate
