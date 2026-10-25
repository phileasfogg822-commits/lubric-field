package com.lubrcapp.lubrcapp.feature.accounts

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.CreditCheck
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.PostingEngine
import com.lubrcapp.lubrcapp.domain.accounting.PostingResult
import com.lubrcapp.lubrcapp.domain.accounting.PostingViolation
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first accounts feature: observes Room and queues writes without network access. */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "accounts")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "accounts")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "accounts", id, payloadJson, revision)

    // Phase 20.1 — typed journal surface over "journal_entries"
    // (local-only; the 20.2 posting engine is the balance gate).

    /** Journal entries ledger-sorted by date, then createdAt, then id. */
    fun observeJournalEntries(scope: OperationalScope): Flow<List<JournalEntryRecord>> =
        repository.observe(scope, "journal_entries").map { rows ->
            rows.map { JournalEntryJson.decode(it.id, it.json) }.sortedJournalEntriesForDisplay()
        }

    /** Journal entries ledger-sorted by date, then createdAt, then id. */
    suspend fun loadJournalEntries(scope: OperationalScope): List<JournalEntryRecord> =
        repository.list(scope, "journal_entries")
            .map { JournalEntryJson.decode(it.id, it.json) }
            .sortedJournalEntriesForDisplay()

    /** Persist an entry exactly as given; balance is enforced by the 20.2 engine, not here. */
    suspend fun saveJournalEntry(scope: OperationalScope, entry: JournalEntryRecord) {
        repository.save(scope, "journal_entries", entry.id, JournalEntryJson.encode(entry))
    }

    /**
     * Post a new entry: fresh id + timestamp, lines exactly as
     * given. Returns the saved record. Balance is NOT checked
     * here — the 20.2 engine is the gate.
     */
    suspend fun postJournalEntry(
        scope: OperationalScope,
        date: String,
        description: String,
        lines: List<JournalLineRecord>,
        referenceNumber: String? = null,
    ): JournalEntryRecord {
        val entry = JournalEntryRecord.of(date, description, lines, referenceNumber)
        saveJournalEntry(scope, entry)
        return entry
    }

    // Distinct name on purpose: same-named member-extensions on
    // different List element types clash on the JVM after type
    // erasure (Phase 19.6 lesson).
    private fun List<JournalEntryRecord>.sortedJournalEntriesForDisplay(): List<JournalEntryRecord> =
        sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))

    // Phase 20.2 — document posting: engine gate, then the
    // journal record + transaction rows are saved together.

    /** The outcome of posting a document through the engine. */
    sealed class DocumentPostResult {
        /** The saved entry and its transaction rows. */
        data class Posted(
            val entry: JournalEntryRecord,
            val transactions: List<AccountTransactionRecord>,
        ) : DocumentPostResult()

        /** The engine rejected the posting; nothing was saved. */
        data class Rejected(val violations: List<PostingViolation>) : DocumentPostResult()
    }

    /**
     * Post a document: the engine validates (totals, balance,
     * one-sided lines, §18 credit) and builds the entry; this
     * layer then saves the journal record AND one
     * `account_transactions` row per line. Entry and row ids
     * derive from the transaction id, so re-posting the same
     * document overwrites the same records instead of
     * duplicating them. On rejection nothing is saved.
     */
    suspend fun postDocument(
        scope: OperationalScope,
        posting: DocumentPosting,
        date: LocalDate,
        description: String = "${posting.documentType} ${posting.documentNumber}",
        now: Instant,
        deviceId: DeviceId,
        creditCheck: CreditCheck? = null,
    ): DocumentPostResult {
        val result = PostingEngine.post(
            posting = posting,
            tenantId = TenantId(scope.tenantId),
            businessId = BusinessId(scope.businessId),
            date = date,
            description = description,
            now = now,
            deviceId = deviceId,
            creditCheck = creditCheck,
        )
        if (result is PostingResult.Rejected) return DocumentPostResult.Rejected(result.violations)
        val domainEntry = (result as PostingResult.Posted).entry
        val entry = JournalEntryRecord(
            id = domainEntry.id.value,
            date = domainEntry.date.toString(),
            description = domainEntry.description,
            referenceNumber = domainEntry.referenceNumber,
            lines = domainEntry.lines.map { line ->
                JournalLineRecord(
                    accountId = line.accountId.value,
                    debit = line.debit.amount,
                    credit = line.credit.amount,
                    memo = line.memo,
                )
            },
            createdAt = now.toString(),
        )
        val transactions = AccountTransactionRecord.fromEntry(entry)
        saveJournalEntry(scope, entry)
        transactions.forEach { row ->
            repository.save(scope, "account_transactions", row.id, AccountTransactionJson.encode(row))
        }
        // Phase 22.3 — the posting's party effect becomes a
        // "party_ledger" row in the same save: this is the
        // job 20.2 deliberately left to Phase 22. A posting
        // without an effect records nothing; a re-post
        // overwrites the same row id ("je-<transactionId>"),
        // which is also what makes the 21.x backfill
        // idempotent. On rejection nothing is saved (the
        // early return above already ran).
        PartyLedgerRecord.fromPosting(posting, entry)?.let { row ->
            savePartyLedgerRow(scope, row)
        }
        return DocumentPostResult.Posted(entry, transactions)
    }

    /** Transaction rows for an entry, ledger-sorted by id (stable line order). */
    suspend fun loadTransactionsForEntry(scope: OperationalScope, entryId: String): List<AccountTransactionRecord> =
        repository.list(scope, "account_transactions")
            .map { AccountTransactionJson.decode(it.id, it.json) }
            .filter { it.entryId == entryId }
            .sortedBy { it.id }

    // Phase 22.3 — typed party-ledger surface over
    // "party_ledger" (sync-whitelisted, INSERT_ONLY).

    /** Persist a ledger row exactly as given. */
    suspend fun savePartyLedgerRow(scope: OperationalScope, row: PartyLedgerRecord) {
        repository.save(scope, "party_ledger", row.id, PartyLedgerJson.encode(row))
    }

    /** All ledger rows, chronological, then by id. */
    suspend fun loadLedgerRows(scope: OperationalScope): List<PartyLedgerRecord> =
        repository.list(scope, "party_ledger")
            .map { PartyLedgerJson.decode(it.id, it.json) }
            .sortedWith(compareBy({ it.date }, { it.id }))

    /** Ledger rows for one party, chronological, then by id. */
    suspend fun loadLedgerRowsForParty(scope: OperationalScope, partyId: String): List<PartyLedgerRecord> =
        loadLedgerRows(scope).filter { it.partyId == partyId }

    // Phase 20.3 — typed chart-of-accounts surface over
    // "accounts" (sync-whitelisted master data). The raw
    // observe/load/save above stay for the legacy screen
    // until 20.4 replaces it.

    /** Accounts sorted by code, then name, then id. */
    fun observeAccounts(scope: OperationalScope): Flow<List<AccountRecord>> =
        repository.observe(scope, "accounts").map { rows ->
            rows.map { AccountJson.decode(it.id, it.json) }.sortedAccountsForDisplay()
        }

    /** Accounts sorted by code, then name, then id. */
    suspend fun loadAccounts(scope: OperationalScope): List<AccountRecord> =
        repository.list(scope, "accounts")
            .map { AccountJson.decode(it.id, it.json) }
            .sortedAccountsForDisplay()

    /** Only active accounts, in display order. */
    suspend fun loadActiveAccounts(scope: OperationalScope): List<AccountRecord> =
        loadAccounts(scope).filter { it.isActive }

    // Phase 24.1 — trial-balance report over a date range:
    // the 20.4 preview promoted to the report engine's
    // first report. Bounds are ISO dates, inclusive.
    suspend fun loadTrialBalance(
        scope: OperationalScope,
        fromDate: String,
        toDate: String,
    ): TrialBalance =
        trialBalanceForPeriod(loadJournalEntries(scope), loadAccounts(scope), fromDate, toDate)

    // Phase 24.2 — profit & loss report over a date range.
    // Bounds are ISO dates, inclusive.
    suspend fun loadProfitAndLoss(
        scope: OperationalScope,
        fromDate: String,
        toDate: String,
    ): ProfitAndLoss =
        profitAndLoss(loadJournalEntries(scope), loadAccounts(scope), fromDate, toDate)

    // Phase 24.3 — balance-sheet report as of a date
    // (cumulative: every entry dated on or before it).
    suspend fun loadBalanceSheet(
        scope: OperationalScope,
        asOfDate: String,
    ): BalanceSheet =
        balanceSheet(loadJournalEntries(scope), loadAccounts(scope), asOfDate)

    /** Persist an account exactly as given. */
    suspend fun saveAccount(scope: OperationalScope, account: AccountRecord) {
        repository.save(scope, "accounts", account.id, AccountJson.encode(account))
    }

    /**
     * Deactivate an account (master data is never deleted, so
     * historical journal lines stay resolvable). No-op for an
     * unknown id.
     */
    suspend fun deactivateAccount(scope: OperationalScope, accountId: String) {
        val account = loadAccounts(scope).firstOrNull { it.id == accountId } ?: return
        saveAccount(scope, account.copy(isActive = false))
    }

    private fun List<AccountRecord>.sortedAccountsForDisplay(): List<AccountRecord> =
        sortedWith(compareBy({ it.code }, { it.name }, { it.id }))
}
