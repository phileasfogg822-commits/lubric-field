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

Status: ACCEPTED on `d72958f`.

`PostingEngine` in domain/accounting (pure Kotlin/JVM, zero
Gradle changes): the single gate from a 17.4
`DocumentPosting` to a balanced domain `JournalEntry` — runs
`PostingValidator` (totals reconciliation, balance,
one-sided lines, party consistency, §18 credit block) and
builds the entry only on zero violations
(`PostingResult.Posted` / `Rejected`). The entry id derives
from the transaction id ("je-<transactionId>"), so
re-posting the same document overwrites the same entry
instead of double-posting; tenant/business/date/currency
context are the caller's (the 19.1 / 20.1 pattern). The
engine writes nothing and posts no stock movements or party
effects itself — those are the Phase 19 and Phase 22
ledgers' jobs from the same posting.

Feature layer: `AccountTransactionRecord` + tolerant
`AccountTransactionJson` over "account_transactions"
(whitelisted AND INSERT_ONLY — the syncing per-line truth
mirroring the local journal entry; stable row ids
"<entryId>-<index>"). `AccountsViewModel.postDocument`
runs the engine, then saves the journal record AND one
transaction row per line; on rejection nothing is saved.
`loadTransactionsForEntry` reads an entry's rows in stable
line order.

Failure history: first head `b667695` failed all three
workflows at `:feature:accounts:compileDebugKotlin` — the
codec's minimal-record fallback called
`AccountTransactionRecord(id = id)` but `entryId` and
`accountId` had no defaults (20.1's record defaulted
everything except `id`; two were missed here). Fix head
`d72958f` (2 lines) defaults both to "". Lesson:
constructor-call cross-check at staging time — static
shape checks cannot catch type errors (same family as
19.5's dropped parameters).

Tests (13): PostingEngineTest (6 — valid posting produces
a balanced entry with derived id, unbalanced rejected,
empty document rejected, two-sided line rejected,
credit-blocked rejected, credit-limit-exceeded rejected) +
AccountTransactionTest (7 — transaction codec round-trip +
garbage tolerance, fromEntry stable ids, postDocument saves
entry + rows, re-post overwrites instead of duplicating,
rejected posting saves nothing).

Verification (GitHub Actions on exact head `d72958f`,
PR #29): Native Android CI run 40074878588 — completed
success; Compose UI Emulator Tests run 40074878581 —
completed success; Phase 12 Segmented Verification run
40074878602 — completed success. 20.2 ACCEPTED.

## Package 20.3 — Chart of Accounts

Status: ACCEPTED on `ece4a06`.

`AccountRecord` (id, code, name, type §74, parentId?,
isActive, createdAt) + tolerant `AccountJson` codec over
"accounts" (sync-whitelisted master data, NOT insert-only —
unlike the local-only journal entries). Documented codec
migrations: a missing or unknown type decodes as
OTHER_INCOME (the catch-all class), and a missing name
falls back to the legacy Phase 12 "description" field, so
pre-20.3 rows keep displaying. `toDomainAccount(tenantId,
businessId, deviceId, now)` bridges to domain/accounting's
`Account`. ViewModel surface: `observeAccounts` /
`loadAccounts` (sorted code+name+id), `loadActiveAccounts`,
`saveAccount`, `deactivateAccount` (master data is never
deleted, so historical journal lines stay resolvable; no-op
for an unknown id). The raw observe/load/save surface
stays for the legacy screen until 20.4 replaces it.

Tests (6): AccountRecordTest — codec round-trip,
garbage/unknown-type tolerance, legacy-description name
fallback, domain bridge, save/load/deactivate round-trip,
sort by code then name.

Verification (GitHub Actions on exact head `ece4a06`,
PR #29): Native Android CI run 40076172375 — completed
success; Compose UI Emulator Tests run 40076172374 —
completed success; Phase 12 Segmented Verification run
40076172393 — completed success. 20.3 ACCEPTED.

## Package 20.4 — Screens

Status: NOT STARTED.

## Package 20.5 — Phase gate

Status: NOT STARTED.
