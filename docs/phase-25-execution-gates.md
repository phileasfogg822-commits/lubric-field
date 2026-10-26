# Phase 25 execution gates — Documents / PDF / Printing

Branch `phase-25-documents-printing`, base
`phase-24-reporting-reconciliation` @ `1846e03`. Packages land on this
branch one at a time, each accepted only with green CI
on its exact head. This doc is the ledger; the design
detail lives in `agent_notes/phase-25-prep.md` (goal workspace,
to be written from the roadmap and the master spec
before 25.1).

## Scope

Documents, PDF generation, and printing
(BusinessOS Roadmap V2, Phase 25). Which
documents, which render path, and which
sharing/printing surfaces ship is settled from
the roadmap and the master spec at planning
time — nothing beyond the phase name is
committed here.

## Status

NOT STARTED. Branch + scaffold pushed 2026-10-25;
draft PR awaits the owner's tap. Package
breakdown is settled from the roadmap and the
master spec at planning time — nothing below is a
package commitment yet.

## Package 25.1 — Printable document model

Status: ACCEPTED on `dbc36e0` (2026-10-25). Trio green:
Native 40219779926, Compose 40219779994, Segmented
40219779947. 3 new files, 1 modified, 5 new tests
(all in `PrintableDocumentTest`).

Contents: `PrintableDocument` + `PrintableLine`
in a new feature/printing module (plain data, no
feature or domain dependency; the app shell maps
sales, purchase, and report data into it).
The `printableDocument` builder computes
subtotal from line amounts as given and grand
total as subtotal + tax − discount; negative
tax or discount throws. Currency is a plain
code string, "" when the source carries none.
No render path yet — PDF, preview, and thermal
all build on this model.

## Package 25.2 — Document layout engine

Status: ACCEPTED on `210ab80` (2026-10-25). Trio green:
Native 40221120896, Compose 40221120940, Segmented
40221120829. 2 new files, 1 modified, 5 new tests
(all in `DocumentLayoutTest`).

Contents: `layoutDocument` in feature/printing —
the pure layout layer beneath every renderer.
It turns a PrintableDocument into positioned
`LayoutLine`s (text, alignment, y, size, bold)
grouped into `LayoutPage`s, with vertical flow
and pagination (line height size × 1.25).
Content order: title, number/date, party (when
present), item lines, subtotal, non-zero
tax/discount lines, grand total, notes, footer.
No framework classes — the layout tests on the
JVM; the PDF writer (25.3) and thermal
renderer resolve alignment to x positions.

## Package 25.3 — PDF writer

Status: ACCEPTED on `09f4f76` (trio Native 40224171952 / Compose 40224172003 attempt 2 / Segmented 40224171978, all green; verified first-hand).

Design: the first renderer — `writePdf` in
feature/printing renders a DocumentLayout to
PDF with the platform PdfDocument API (no new
dependency). X positions resolve from the
layout's semantic alignment via the pure
`resolveX` (LEFT at the margin, CENTER
midpoint, RIGHT before the margin); the
baseline is the line top minus the Paint
ascent. Layout dims and PDF dims share the
same point space. The writer itself is
framework code, so its behavior is covered by
an emulator test asserting valid PDF bytes
(%PDF header), and the Compose workflow list
gains :feature:printing so that test actually
runs in CI.

Evidence: first push `5e4ac58` (3 new files —
PdfWriter, XResolutionTest, PdfWriterTest — plus
the printing build file and the Compose
workflow list); fix head `09f4f76` adds only
`androidx.test:runner:1.5.2` to printing's
androidTest deps. Tests: 4 unit (XResolution)
+ 1 instrumentation (PdfWriterTest).
Incidents: (1) the first head failed
Compose-only because the printing test APK
lacked AndroidJUnitRunner — 25.1's minimal
build file declared only ext:junit, which does
not bring the runner; the explicit runner dep
fixed it. (2) The fix head's attempt 1 failed
Compose-only on inventory's
adjustFlowPostsAMovement, with inventory code
identical to `5e4ac58` where the same test
passed and a diff touching only printing's
test deps — a timing flake; "Re-run failed
jobs" (attempt 2) went green with no code
change. Note the build stopped at inventory
on attempt 1, so printing's emulator test
first actually ran on attempt 2.

## Package 25.4 — Thermal receipt renderer

Status: ACCEPTED on `89ffdf2` (trio Native 40226350807 / Compose 40226350848 / Segmented 40226350770, all green; verified first-hand).

Design: the ESC/POS half of spec §98's
abstraction layer — `thermalReceipt` in
feature/printing renders a DocumentLayout to
the byte stream a thermal receipt printer
consumes, the same layout writePdf renders
to PDF, so every document has two renderers
and no second layout pass. Pure Kotlin, no
framework classes, fully JVM-testable:
ESC @ initialize, ESC a n alignment (0 LEFT,
1 CENTER, 2 RIGHT), ESC E n bold on/off,
LF per line, GS V 0 full cut once at the
end. Page boundaries are ignored — receipt
paper is continuous. Text is US-ASCII
(non-ASCII folds to '?') and lines longer
than widthChars (default 42) truncate, since
a thermal line has a fixed column count.
Deliberately OUT: device transport
(Bluetooth / USB / network — moves bytes,
does not decide them, and no CI runner can
prove it), QR and logo bitmaps (need a
bitmap/QR encoder), and the cash-drawer
kick (needs a device signal the document
model does not carry).

Evidence: 2 new files (ThermalReceipt,
ThermalReceiptTest), 8 JVM tests, no new
dependencies, no workflow changes. The
watch for this head died on a runtime
restart with Native still in progress and
was restarted after a direct API check
(Segmented + Compose already green);
the restarted watch delivered the final
Native green.

## Package 25.5 — Print settings model

Status: ACCEPTED on `e41b563` (trio Native 40227563121 / Compose 40227563098 / Segmented 40227563087, all green; verified first-hand).

Design: the paper and finishing choices a
printer-settings screen (spec screen 67)
will edit and the renderers consume —
`PrintSettings` in feature/printing, pure
data with no storage (persistence rides
the app's usual record surface when the
settings screen lands, not this layer).
`PaperWidth` maps to the thermal column
count the receipt renderer truncates to:
58 mm carries 32 columns, 80 mm carries
42 — the conventional defaults, and the
80 mm value keeps 25.4's default. `autoCut`
selects the trailing GS V 0 cut; it arrives
as an additive defaulted parameter on
`thermalReceipt`, so every 25.4 test still
passes unchanged. Deliberately OUT: the
settings screen itself, persistence, and
any device connection state — those need
shell wiring and a record surface, and
this package is the model they will edit.

Evidence: 2 new files (PrintSettings,
PrintSettingsTest), 1 modified
(ThermalReceipt gains the autoCut
parameter), 4 tests, no new dependencies,
no workflow changes.

## Package 25.6 — Print preview screen

Status: ACCEPTED on `b4c17c7` (trio Native 40228829090 / Compose 40228829093 / Segmented 40228829070, all green; verified first-hand).

Design: spec §99's Preview action —
`PrintPreviewScreen` in feature/printing
renders a DocumentLayout's lines as text
rows (alignment from the line's semantic
align, bold from its bold flag, size from
its point size) inside a scrolling Column,
with the §99 actions (Share, Download PDF,
Print) as buttons in a fixed row above the
content. The actions are callbacks the
shell wires later (25.7): this package
owns the preview UI, not the share sheet,
the file write, or the printer. Build
changes mirror feature/accounts' compose
subset (compose plugin, compose-bom
2026.06.00, ui, ui-graphics,
ui-tooling-preview, material3, ui-test
deps) — printing's first Compose code, so
its build file grows from minimal to the
pattern module's shape. Static labels go
through the module's own strings.xml (the
Native boundary step rejects hardcoded
Text literals); document lines are dynamic
data. Tests (emulator): title + total
after scroll (30 items, so the total is
genuinely below the fold), and all four
action buttons firing their callbacks.
Deliberately OUT: the registry entry and
the document source — the shell wiring
that gives the screen a real document is
25.7, not this package.

Evidence: 2 new files (PrintPreviewScreen,
PrintPreviewScreenTest) + 1 new res file
(strings.xml) + the printing build file
grown to the compose subset; 2 emulator
tests, both green on the first head — the
scroll test and the callback test passed
with no fix cycle.

## Package 25.7 — Shell wiring: registry entry, latest-invoice preview, Share

Status: ACCEPTED on `925060f` (trio Native 40279174841 / Compose 40279174914 / Segmented 40279174825, all green; verified first-hand). Package head `63f44a2` (17 paths: mapper, preview content, share helper, FileProvider wiring, app strings, printing dependency, 10 new tests, 3 registry-list updates) took two fix heads: `3dcb1a1` (test-only — printing's key added to ResolvedShellVisibilityTest's hand-kept gated set and its expected list, after Native failed on that test alone) and `925060f` (share chooser title via stringResource, after Native lint flagged context.getString in composition). Tests added: 5 mapper, 2 registry, 2 share, 1 preview hidden-actions.

Design: the shell wiring that gives the
preview screen a real document and a real
action. (1) Registry entry: PRINTING_ID
"printing", titleRes app
R.string.feature_printing ("Printing"),
configKey FeatureFlagKeys.MODULE_PRINTING
and viewCapability Capabilities.
PRINTING_VIEW ("printing.view", added to
the catalog) — both keys already exist
or, for the capability, extend the
catalog the established way, so the
entry is gated by both gates like every
other module; it appears in the More
list automatically. Staging corrected
the first draft here (null/null gates),
because a selectable module with no view
capability would stay visible under
empty grants — wrong for invoice data.
(2) Document source: the shell's
printing content lambda collects
SalesViewModel.observeInvoices(scope) —
the same activity-scoped ViewModel
pattern the parties/sales entries use —
plus parties for the party name and lines
for the chosen invoice, and a shell-side
mapper turns the LATEST invoice into a
PrintableDocument via the totals engine
(never re-derived): latest = greatest ISO
date, ties broken by greatest id, both
deterministic. Line amounts are the
engine's taxable bases, not its line
totals — the builder's grand total is
subtotal + tax, so tax-inclusive line
amounts would double-count the tax.
Currency defaults to XXX, the sales
screen's own default, until the app-wide
currency wiring lands. No invoices
renders a shell-side empty state ("No
invoices yet", app strings) with Back —
never a preview of nothing. (3) Share
wired: an app-module share helper writes
the PDF bytes (writePdf) to a cache file
and builds the ACTION_SEND chooser
intent through a FileProvider URI. The
manifest EXISTS (app/src/main/
AndroidManifest.xml — correcting the
earlier "AGP generates it" note), so
this is a <provider> entry plus
res/xml/file_paths.xml, no manifest
spike and no new dependency versions,
since the app module already has
androidx.core. Emulator test: cache
write + provider URI + intent action/
type/stream/grant flags. (4) No dead
buttons (the 13.4 rule):
onDownload/onPrint become nullable
callbacks on PrintPreviewScreen (default
null, button hidden when null); 25.6's
tests pass all callbacks and still pass.
Download (MediaStore) and system Print
(PrintManager adapter) are 25.8/25.9, not
this package. Registry-list tests that
pin exact module lists gain the printing
entry (registry order, navigation
modules, and the one capability-gated
list where printing is granted and
config-enabled). Deliberately OUT: a
document picker list (a later package —
latest-invoice is the v1 entry), sales-
screen Preview buttons (the invoice →
preview jump needs a feature-to-feature
navigation mechanism this shell does not
have yet).
