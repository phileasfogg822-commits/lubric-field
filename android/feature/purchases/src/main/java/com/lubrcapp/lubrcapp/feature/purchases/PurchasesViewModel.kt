package com.lubrcapp.lubrcapp.feature.purchases

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Offline-first purchases feature: observes Room and queues writes without network access. */
@HiltViewModel
class PurchasesViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "purchase_orders")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "purchase_orders")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "purchase_orders", id, payloadJson, revision)
}
