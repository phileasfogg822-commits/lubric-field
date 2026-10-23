package com.lubrcapp.lubrcapp.feature.parties

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first parties feature: observes Room and queues writes without network access. */
@HiltViewModel
class PartiesViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "customers")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "customers")

    /** Customers are nonfinancial master data: the repository soft-deletes with a tombstone and queues the DELETE. */
    suspend fun delete(scope: OperationalScope, id: String) = repository.delete(scope, "customers", id)

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "customers", id, payloadJson, revision)

    // Phase 14.1 — typed access over the same store. The codec tolerates
    // legacy free-form payloads, so typed reads are safe on old records;
    // the raw methods above stay until the 14.2 form migrates the screen.

    /** Typed parties: every record decoded through [PartyJson]. */
    fun observeParties(scope: OperationalScope): Flow<List<Party>> =
        observe(scope).map { records -> records.map { PartyJson.decode(it.id, it.json) } }

    suspend fun loadParties(scope: OperationalScope): List<Party> =
        load(scope).map { PartyJson.decode(it.id, it.json) }

    /** Saves a typed party through the codec (revision passes through to the store). */
    suspend fun saveParty(scope: OperationalScope, party: Party, revision: Long = 0) =
        save(scope, party.id, PartyJson.encode(party), revision)
}
