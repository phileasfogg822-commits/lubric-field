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
    // legacy free-form payloads, so typed reads are safe on old records.
    // Since 14.2 the screen uses only this typed surface (plus delete);
    // the raw methods above remain as the low-level store passthrough,
    // pinned by PartiesViewModelTest.

    /** Typed parties: every record decoded through [PartyJson]. */
    fun observeParties(scope: OperationalScope): Flow<List<Party>> =
        observe(scope).map { records -> records.map { PartyJson.decode(it.id, it.json) } }

    suspend fun loadParties(scope: OperationalScope): List<Party> =
        load(scope).map { PartyJson.decode(it.id, it.json) }

    /** Saves a typed party through the codec (revision passes through to the store). */
    suspend fun saveParty(scope: OperationalScope, party: Party, revision: Long = 0) =
        save(scope, party.id, PartyJson.encode(party), revision)

    // Phase 14.3 — customer groups (§19) as their own record type in
    // the same store. Deleting a group uses the same tombstone path as
    // parties and never touches the parties themselves: their groupId
    // link stays and renders unresolved until the group exists again.

    /** Typed customer groups: every record decoded through [PartyGroupJson]. */
    fun observeGroups(scope: OperationalScope): Flow<List<PartyGroup>> =
        repository.observe(scope, "customer_groups")
            .map { records -> records.map { PartyGroupJson.decode(it.id, it.json) } }

    suspend fun loadGroups(scope: OperationalScope): List<PartyGroup> =
        repository.list(scope, "customer_groups").map { PartyGroupJson.decode(it.id, it.json) }

    suspend fun saveGroup(scope: OperationalScope, group: PartyGroup, revision: Long = 0) =
        repository.save(scope, "customer_groups", group.id, PartyGroupJson.encode(group), revision)

    /** Groups are nonfinancial master data: soft-delete by tombstone, like parties. */
    suspend fun deleteGroup(scope: OperationalScope, id: String) =
        repository.delete(scope, "customer_groups", id)
}
