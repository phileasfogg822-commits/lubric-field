# Phase 28 — Global Search / Import-Export / Backup / Data Lifecycle — execution gates

Base: `phase-27-field-sales` @ `5f0c5b7`.

## Package 28.1 — Global search

Global search (§129) in domain/business:
`SearchEntry` (group, id, title, detail,
keywords) + `SearchGroup` (exactly the §129
list, in spec order) + `GlobalSearch.search`
(case-insensitive substring over title, detail
and keywords, so names, document numbers, item
names, phones and barcodes all match through
one rule; empty/blank query returns nothing;
empty groups omitted; input order kept within
a group). Pure model over plain entries — the
screen layer maps real records to entries, so
no index tables and no persistence. 8 JVM tests.

Status: ACCEPTED on `3e3edaf`.

## Package 28.2 — CSV export

Status: NOT STARTED.

## Package 28.3 — CSV import + mapping

Status: NOT STARTED.

## Package 28.4 — Backup snapshot

Status: NOT STARTED.

## Package 28.5 — Restore rules

Status: NOT STARTED.

## Package 28.6 — Archive rules

Status: NOT STARTED.

## Package 28.7 — Search/export/backup screens + shell wiring

Status: NOT STARTED.

## Package 28.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

28.1 ACCEPTED 2026-10-27 on `3e3edaf` (trio Native 40384708966 / Compose 40384709095 / Segmented 40384708811, all green, first head): global search model in domain/business, 8 JVM tests.
