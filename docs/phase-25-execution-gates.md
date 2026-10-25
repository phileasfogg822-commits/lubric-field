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

Status: NOT STARTED.

Design: the single shape every render path
consumes — `PrintableDocument` + `PrintableLine`
in a new feature/printing module (plain data, no
feature or domain dependency; the app shell maps
sales, purchase, and report data into it, because
feature modules never depend on each other).
The `printableDocument` builder computes
subtotal from line amounts as given (source
engines own per-line rounding) and grand total
as subtotal + tax − discount; tax and discount
are non-negative magnitudes (negative values
throw). Currency is a plain code string, ""
when the source carries none. No render path
yet — PDF, preview, and thermal all build on
this model.
