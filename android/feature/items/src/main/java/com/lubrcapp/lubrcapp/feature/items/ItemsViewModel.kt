package com.lubrcapp.lubrcapp.feature.items

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first items feature: observes Room and queues writes without network access. */
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "products")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "products")

    suspend fun delete(scope: OperationalScope, id: String) = repository.delete(scope, "products", id)

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "products", id, payloadJson, revision)

    // Phase 15.1 — typed access over the same store. The codec tolerates
    // legacy free-form payloads, so typed reads are safe on old records.
    // The raw methods above remain as the low-level store passthrough,
    // pinned by ItemsViewModelTest; the screen moves to this typed
    // surface in Package 15.2.

    /** Typed items: every record decoded through [ItemJson]. */
    fun observeItems(scope: OperationalScope): Flow<List<Item>> =
        observe(scope).map { records -> records.map { ItemJson.decode(it.id, it.json) } }

    suspend fun loadItems(scope: OperationalScope): List<Item> =
        load(scope).map { ItemJson.decode(it.id, it.json) }

    /** Saves a typed item through the codec (revision passes through to the store). */
    suspend fun saveItem(scope: OperationalScope, item: Item, revision: Long = 0) =
        save(scope, item.id, ItemJson.encode(item), revision)
}
