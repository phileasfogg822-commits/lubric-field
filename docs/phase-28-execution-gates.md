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

Backup snapshot (§124–§126) in domain/business:
`BackupManifest` (date, business, per-table
record counts) + `BackupSnapshot` (manifest +
tables) + `BackupSnapshotOps.create` (counts
computed from the tables, so a created snapshot
always verifies), `verify` (lists every
inconsistency — blank name/date, count
disagreements, manifest/snapshot table mismatches;
empty means valid), `fileName` (§126:
`business_<ISO date>.busbackup`, non-ISO dates
rejected). No encryption (key management is an
owner decision) and no cloud scheduling (no
endpoint exists) — both named, not invented.
9 JVM tests.

Status: ACCEPTED on `382656f`.

## Package 28.5 — Restore rules

Restore rules (§127) in domain/business:
`RestorePreview` (date, business name, record
counts, verification problems, total), `decide`
(Ready vs Blocked), `canRestore` (valid AND
explicitly confirmed — neither half alone
restores, which is "never overwrite silently" as
a rule). No restore writes here — applying a
snapshot's tables is a shell-layer job needing
the whitelist and posting rules. 5 JVM tests.

Status: ACCEPTED on `0491d72`.

## Package 28.6 — Archive rules

Archive rules (§209) in domain/business:
`Archivable` (item + flag) + `ArchiveRules`
(archive/restore idempotent transitions,
`visible` excludes archived, `history` keeps
everything — archive is a flag and a visibility
rule, never delete semantics). 7 JVM tests.

Status: ACCEPTED on `16e7111`.

## Package 28.7 — Search/export/backup screens + shell wiring

New `feature/business` module (mirroring
printing's build, runner included) with
SearchScreen (§129, stateless over 28.1 grouped
results), ExportScreen (§86/§169, 28.2 CSV,
Share button only with a callback) and
BackupScreen (§127, 28.5 preview, Restore gated
on valid + confirmed, clickable confirm row).
Shell `DataSearchMapper` (documents/items/
parties → search entries, documents → CSV) +
`DataToolsContent` (search/export/backup
sections over real records). Registry DATA_ID
reusing MODULE_BACKUP + new DATA_VIEW
capability; rosters updated. Two fix heads:
`e18deb3` (app R import + plain-String
businessId) and `b3c57af` (MODULE_BACKUP in
the unknown-keys visibility fixture). 17 tests
(11 emulator + 4 mapper JVM + 2 registry JVM).

Status: ACCEPTED on `b3c57af`.

## Package 28.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

28.1 ACCEPTED 2026-10-27 on `3e3edaf` (trio Native 40384708966 / Compose 40384709095 / Segmented 40384708811, all green, first head): global search model in domain/business, 8 JVM tests.

28.2 ACCEPTED 2026-10-27 on `fdd89da` (trio Native 40387583865 / Compose 40387583828 / Segmented 40387584064, all green, first head): CSV export writer in domain/business, 8 JVM tests.

28.3 ACCEPTED 2026-10-27 on `bfacac6` (trio Native 40391128526 / Compose 40391128504 / Segmented 40391128514, all green, first head): CSV import + mapping + duplicate warnings in domain/business, 13 JVM tests.

28.4 ACCEPTED 2026-10-27 on `382656f` (trio Native 40393799020 / Compose 40393799102 / Segmented 40393799025, all green, first head): backup snapshot + verification in domain/business, 9 JVM tests.

28.5 ACCEPTED 2026-10-27 on `0491d72` (trio Native 40397112633 / Compose 40397112668 / Segmented 40397112634, all green, first head): restore rules in domain/business, 5 JVM tests.

28.6 ACCEPTED 2026-10-27 on `16e7111` (trio Native 40399914923 / Compose 40399914892 / Segmented 40399914877, all green, first head): archive rules in domain/business, 7 JVM tests.

28.7 ACCEPTED 2026-10-27 on `b3c57af` (trio Native 40407064058 / Compose 40407064069 / Segmented 40407064064, all green, after fix heads e18deb3 and b3c57af): search/export/backup screens + shell wiring in feature/business, 17 tests.
