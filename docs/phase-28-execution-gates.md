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

CSV export (§86) in domain/business:
`CsvWriter.write` over headers + rows of plain
strings, RFC 4180 shape: comma/quote/newline/
carriage-return fields quoted with inner quotes
doubled, everything else as-is, CRLF endings
including the last line, misaligned rows and
empty headers rejected rather than silently
written. 8 JVM tests.

Status: ACCEPTED on `fdd89da`.

## Package 28.3 — CSV import + mapping

CSV import (§166–§168) in domain/business:
`CsvParser` (quoted fields, doubled quotes, CRLF
and LF endings, no trailing newline needed;
blank text, empty headers, misaligned rows and
unterminated quotes all throw), `ImportMapper`
(exact header match per §167, unmapped columns
ignored, missing mapped columns and duplicate
headers throw), `ImportDuplicates` (§168 warnings,
not rejects, for caller-chosen key fields; later
rows sharing an earlier key tuple warn with both
indices; all-blank keys never warn). Writes
nothing — the caller turns mapped rows into
records through existing machinery. 13 JVM tests.

Status: ACCEPTED on `bfacac6`.

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

28.2 ACCEPTED 2026-10-27 on `fdd89da` (trio Native 40387583865 / Compose 40387583828 / Segmented 40387584064, all green, first head): CSV export writer in domain/business, 8 JVM tests.

28.3 ACCEPTED 2026-10-27 on `bfacac6` (trio Native 40391128526 / Compose 40391128504 / Segmented 40391128514, all green, first head): CSV import + mapping + duplicate warnings in domain/business, 13 JVM tests.
