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

Tests (10): JournalEntryRecordTest (7 — balance math,
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

Tests (12): PostingEngineTest (6 — valid posting produces
a balanced entry with derived id, unbalanced rejected,
empty document rejected, two-sided line rejected,
credit-blocked rejected, credit-limit-exceeded rejected) +
AccountTransactionTest (6 — transaction codec round-trip +
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

Status: ACCEPTED on `df06d01`.

`TrialBalance.kt` — pure `trialBalance(entries, accounts)`:
per-account debit/credit totals; rows in accounts order for
ids seen in lines, unknown ids appended sorted with name =
id fallback; grand totals; `isBalanced`. `AccountsScreen.kt`
FULL REWRITE (the org.json raw Phase 12 screen is removed):
signature `AccountsScreen(scope, onBack, viewModel =
hiltViewModel())` kept for `AppModuleRegistry.kt:157`;
private `AccountsMode` CHART/JOURNAL; both modes use a
scrolling `Column` (19.4/19.6 lessons — all nodes composed,
tests scroll-first). CHART = code/name fields + AccountType
chips (`AccountType.entries`, enum `.name` as chip text —
data, not a literal) + Save button (enabled ⇔ code+name
non-blank, not saving) + account rows
(`"%1$s · %2$s · %3$s"` code·name·type, inactive rows get
"· Inactive" variant) with per-row Deactivate button (tests
seed exactly ONE account so "Deactivate" is unique).
JOURNAL = trial-balance card (rows format
`"%1$s: Dr %2$s, Cr %3$s"` — deliberately distinct from
line rows `"%1$s · Dr %2$s / Cr %3$s"` to avoid
duplicate-text collisions) + entry rows
`"%1$s · %2$s · Dr %3$s / Cr %4$s"` + detail view (lines
with account names resolved, unknown id → id; entry totals
reuse the trial debit/credit strings) + "Back to journal".
`strings.xml` rewritten: 24 strings (accounts_back/title
kept; old keys accounts_entry_label/edit/cancel_edit
dropped, no stale references).

Tests (9): TrialBalanceTest (4) + AccountsScreenComposeTest
REWRITTEN (5: chartAddAccountFlow, chartDeactivateMarksRow-
Inactive, journalTrialBalanceAndDetailFlow, emptyStatesAnd-
BackNavigation, saveButtonDisabledUntilCodeAndNameEntered;
fake repo is table-aware with a single
`MutableStateFlow<List<Pair<String, OperationalRecord>>>`).

Failure history: first head `74e0611` — Native ✅,
Segmented ✅, Compose ❌ (3 failures, all
AccountsScreenComposeTest: chartAddAccountFlow,
chartDeactivateMarksRowInactive, emptyStatesAndBack-
Navigation — every failure "found but not displayed": the
chart form pushes account rows and the empty state below
the fold, and the tests asserted them visible without
scrolling first). Fix head `df06d01`: the scrolling
content sits in a weighted `Box` (remaining height, not a
fillMaxSize child of a fixed Column) and the tests
`performScrollTo()` before every below-fold
`assertIsDisplayed` (the 19.6 test-fix pattern).

Verification (GitHub Actions on exact head `df06d01`,
PR #29): Native Android CI run 40079039896 — completed
success; Compose UI Emulator Tests run 40079039902 —
completed success; Phase 12 Segmented Verification run
40079039906 — completed success. 20.4 ACCEPTED.

## Package 20.5 — Phase gate

Status: ACCEPTED.

Sweep vs Phase 19 head `10f7359`: 20 files changed,
+2142/−78 (19 code/test/build files + this gates doc).
Packages: 20.1 ACCEPTED on `dd686a1` (docs `889391d`),
20.2 ACCEPTED on `d72958f` (docs `b0b75e7`), 20.3
ACCEPTED on `ece4a06` (docs `9224047`), 20.4 ACCEPTED on
`df06d01` (docs `e3d72cf`). New tests: 37 total (20.1: 10,
20.2: 12, 20.3: 6, 20.4: 9). Test-count correction at this
gate: the 20.1 and 20.2 entries originally claimed 11 and
13; a per-file @Test recount at the gate proved 10
(JournalEntryRecordTest 7, not 8 — the eighth `fun` is a
private helper) and 12 (AccountTransactionTest 6, not 7),
corrected at `f8eb340`. Lesson carried forward: recount
@Test per file at the gate, never trust the package-time
claim.

Deferred from Phase 20 (unchanged from earlier phases):
§213 stock reservations → Phases 21/22; sale-time serial
SOLD flip → Phase 21; currency wiring through
AppModuleRegistry still shell work (totals preview
defaults); 12C-05(b) arrival-order-wins intent still
Umar's call at §12.7. Phase 20 adds no new deferrals: the
full trial-balance report engine stays Phase 24's job —
20.4 ships the preview only.

Phase 21 prep: Sales Fulfilment / Invoices / Returns /
Credit Notes. It is the first consumer of the 20.2 posting
engine for real documents (via `postDocument`), of the
19.x inventory ledger (§213 reservations, serial SOLD
flip), and of domain/sales' invoice lifecycle. Prep doc:
`agent_notes/phase-21-prep.md` in the goal workspace.

Phase 20 COMPLETE 4/4 + gate.
