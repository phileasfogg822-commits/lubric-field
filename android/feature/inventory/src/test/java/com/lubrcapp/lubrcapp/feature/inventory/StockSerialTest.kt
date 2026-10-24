package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSerialTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table]?.toList() ?: emptyList())
        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table]?.toList() ?: emptyList()
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            val rows = tables.getOrPut(table) { mutableListOf() }
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    @Test
    fun codecRoundTripsAndToleratesGarbage() {
        val record = StockSerialRecord(
            id = "sn-1",
            itemId = "tv",
            serialNumber = "SN28282",
            status = SerialStatus.SOLD,
            warehouseId = "wh-main",
            date = "2026-10-24",
            note = "Invoice 12",
            createdAt = "2026-10-24T00:00:00Z",
        )
        val decoded = StockSerialJson.decode("other-id", StockSerialJson.encode(record))
        assertEquals(record.copy(id = "other-id"), decoded)
        val garbage = StockSerialJson.decode("sn-9", "not json")
        assertEquals("sn-9", garbage.id)
        assertEquals("", garbage.serialNumber)
        assertEquals(SerialStatus.IN_STOCK, garbage.status)
        val unknown = StockSerialJson.decode(
            "sn-2",
            """{"itemId":"tv","serialNumber":"SN1","status":"IN_TRANSIT"}""",
        )
        assertEquals(SerialStatus.IN_STOCK, unknown.status)
    }

    @Test
    fun addSerialRegistersInStockAndRejectsBlank() = runBlocking {
        val vm = InventoryViewModel(FakeRepository())
        assertEquals(SerialAddResult.Invalid, vm.addSerial(scope, "tv", "  "))
        val added = vm.addSerial(scope, "tv", " SN28282 ")
        assertTrue(added is SerialAddResult.Added)
        val record = (added as SerialAddResult.Added).record
        assertEquals("SN28282", record.serialNumber)
        assertEquals(SerialStatus.IN_STOCK, record.status)
        assertEquals(listOf(record), vm.loadSerialsForItem(scope, "tv"))
        assertTrue(vm.loadSerialsForItem(scope, "radio").isEmpty())
    }

    @Test
    fun duplicateInStockSerialForSameItemIsRejected() = runBlocking {
        val vm = InventoryViewModel(FakeRepository())
        vm.addSerial(scope, "tv", "SN28282")
        assertEquals(SerialAddResult.Duplicate, vm.addSerial(scope, "tv", "SN28282"))
        // The same serial on a different item is a different unit.
        assertTrue(vm.addSerial(scope, "radio", "SN28282") is SerialAddResult.Added)
        assertEquals(2, vm.loadSerials(scope).size)
    }

    @Test
    fun removeWritesOffAndFreesTheSerialForReRegistration() = runBlocking {
        val vm = InventoryViewModel(FakeRepository())
        val added = vm.addSerial(scope, "tv", "SN28282") as SerialAddResult.Added
        val removed = vm.removeSerial(scope, added.record.id, "Cracked screen")
        assertEquals(SerialStatus.REMOVED, removed?.status)
        assertEquals("Cracked screen", removed?.note)
        // Removing again is a no-op: it is no longer in stock.
        assertNull(vm.removeSerial(scope, added.record.id, "Again"))
        assertNull(vm.removeSerial(scope, "missing-id", "Nope"))
        // The registry keeps the REMOVED record AND accepts a fresh one.
        assertTrue(vm.addSerial(scope, "tv", "SN28282") is SerialAddResult.Added)
        val all = vm.loadSerialsForItem(scope, "tv")
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.status == SerialStatus.IN_STOCK })
    }
}
