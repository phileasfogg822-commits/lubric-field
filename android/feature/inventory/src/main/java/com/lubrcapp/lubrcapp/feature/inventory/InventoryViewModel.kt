package com.lubrcapp.lubrcapp.feature.inventory

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Offline-first inventory feature: observes Room and queues writes without network access. */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "stock_movements")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "stock_movements")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "stock_movements", id, payloadJson, revision)
}
