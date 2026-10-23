package com.lubrcapp.lubrcapp.feature.items

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.4: batches land on their own table through their codec. */
class ItemsViewModelBatchesTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        var savedTable: String? = null
        var deletedTable: String? = null

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table].orEmpty())

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table].orEmpty()

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            savedTable = table
            val list = tables.getOrPut(table) { mutableListOf() }
            list.removeAll { it.id == id }
            list.add(OperationalRecord(id, json))
        }

        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deletedTable = table
            tables[table]?.removeAll { it.id == id }
        }
    }

    @Test fun batchCrudUsesBatchesTableAndCodec() = runBlocking {
        val fake = FakeRepository()
        val vm = ItemsViewModel(fake)
        val batch = Batch(id = "bt-1", itemId = "i-1", batchNumber = "B-1", quantity = BigDecimal("48"))
        vm.saveBatch(scope, batch)
        assertEquals("batches", fake.savedTable)
        assertEquals(listOf(batch), vm.loadBatches(scope))
        assertEquals(listOf(batch), vm.observeBatches(scope).first())

        vm.deleteBatch(scope, "bt-1")
        assertEquals("batches", fake.deletedTable)
        assertEquals(emptyList<Batch>(), vm.loadBatches(scope))
    }

    @Test fun legacyBatchPayloadDecodesThroughViewModel() = runBlocking {
        val fake = FakeRepository()
        fake.tables["batches"] = mutableListOf(
            OperationalRecord("bt-7", """{"uuid":"u1","itemId":"i-1","batchNumber":"Legacy Batch"}"""),
        )
        val vm = ItemsViewModel(fake)
        assertEquals("Legacy Batch", vm.loadBatches(scope).single().batchNumber)
    }
}
