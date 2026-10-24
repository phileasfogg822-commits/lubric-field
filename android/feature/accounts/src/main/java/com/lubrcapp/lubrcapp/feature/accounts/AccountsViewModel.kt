package com.lubrcapp.lubrcapp.feature.accounts

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
}
