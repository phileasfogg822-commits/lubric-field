package com.lubrcapp.lubrcapp.feature.items

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
}
