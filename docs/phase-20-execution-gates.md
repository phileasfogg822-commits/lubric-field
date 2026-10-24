# Phase 20 — Accounting Core & Posting Engine (Roadmap V2)

Branch: `phase-20-accounting-core` (from the Phase 19
candidate, `phase-19-inventory-ledger` at `10f7359`).
Draft PR to follow via the connector. Packages are accepted
only with green-CI proof on the exact SHA, as in Phases 12–19.

## Authoritative scope

Master spec §74 (Chart of Accounts: Assets, Liabilities,
Equity, Income, Cost of Goods Sold, Expenses, Other Income;
custom accounts allowed) and the journal engine section
(balanced journal entries: every entry's debits = credits;
core tables `accounts`, `journal_entries`, `journal_lines`;
canonical examples: credit sale — Customer Receivable Dr /
Sales Revenue Cr; customer payment — Cash Dr / Customer
Receivable Cr). This phase is the first CONSUMER of the
Phase 17.4 posting contract (`DocumentPosting`,
`PostingValidator`, §18 credit block) — the same relationship
Phase 18 was to Phase 17 — and the posting engine its
Phase 21/22/23 document consumers will build on.

Sync posture (pinned at branch time, mirroring Phase 19):
`accounts` IS sync-whitelisted; `account_transactions` IS
whitelisted AND INSERT_ONLY (append-only by platform rule).
`journal_entries` / `journal_lines` are NOT whitelisted →
local-only records, the header-local / movements-sync split
from Phase 19.5: the journal is the local auditable record;
the whitelisted transaction truth syncs. Open pin for the
build (see `agent_notes/phase-20-prep.md`): the engine
writes `account_transactions` rows alongside the local
journal records — recommended yes.

Invariants carried forward: pure domain stays in
`domain/accounting` (JVM-only, no Gradle changes); amounts
as plain strings; Money equality is compareTo-based;
posting is append-only — corrections are reversing entries,
never edits (the §181 stock-movement rule, applied to
money); typed records via `OperationalBusinessRepository`,
tolerant kotlinx codecs, no org.json in new code; Compose
lessons in force (scrolling Column for test-interactive
forms, LazyColumn display-only, `performScrollTo()` before
typing in scrolling containers, distinct helper names).

## Package 20.1 — Journal model + codec

Status: ACCEPTED on `dd686a1`.

`JournalEntryRecord` (id, date, description, referenceNumber?,
lines embedded, createdAt) + `JournalLineRecord` (accountId,
debit/credit as plain BigDecimals, memo?) over
"journal_entries" (local-only, not whitelisted); `isBalanced`
(BigDecimal totals comparison) and `totalDebits` /
`totalCredits` report balance without gating — the 20.2
engine is the gate. `toDomainEntry(tenantId, businessId,
currency, deviceId, now)` bridges to domain/accounting's
`JournalEntry` (the 19.1 `toPostingMovement(currency)`
pattern; line ids deterministic "<entryId>-<index>"). Tolerant
`JournalEntryJson` codec (unknown/missing → defaults,
unparseable payload → minimal record with id only, record id
wins, unparseable amount → ZERO). ViewModel surface:
`observeJournalEntries` / `loadJournalEntries` (sorted
date+createdAt+id), `saveJournalEntry`, `postJournalEntry`
(fresh UUID + timestamp). The `accounts` module gained
`:domain:accounting` and `kotlinx-serialization-json:1.6.2`
(the 19.1 dependency-diff lesson, applied before
integrating). Existing raw accounts surface untouched.

Tests (11): JournalEntryRecordTest (8 — balance math,
unbalanced report, codec round-trip, garbage/unknown-field
tolerance, unparseable-amount-as-ZERO + id-wins, domain
bridge with caller currency, factory stamps) +
AccountsJournalViewModelTest (3 — save/load round-trip on
"journal_entries", post stamps fresh id + timestamp, sort by
date/createdAt/id).

Verification (GitHub Actions on exact head `dd686a1`,
PR #29): Native Android CI run 40073023834 — completed
success; Compose UI Emulator Tests run 40073023812 —
completed success; Phase 12 Segmented Verification run
40073023822 — completed success. 20.1 ACCEPTED.

## Package 20.2 — Posting engine

Status: NOT STARTED.

## Package 20.3 — Chart of Accounts

Status: NOT STARTED.

## Package 20.4 — Screens

Status: NOT STARTED.

## Package 20.5 — Phase gate

Status: NOT STARTED.
