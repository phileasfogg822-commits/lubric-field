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

    @Test
    fun flipSoldFlipsOnlyMatchingInStockSerials() {
        val serials = listOf(
            StockSerialRecord(id = "s-1", itemId = "tv", serialNumber = "SN1"),
            StockSerialRecord(id = "s-2", itemId = "tv", serialNumber = "SN2"),
            StockSerialRecord(id = "s-3", itemId = "tv", serialNumber = "SN3", status = SerialStatus.SOLD),
            StockSerialRecord(id = "s-4", itemId = "radio", serialNumber = "SN1"),
        )
        val result = flipSerialsToSold(serials, "tv", listOf("SN1", "SN3", "SN9", "SN1"))
        assertEquals(listOf("SN1"), result.flipped.map { it.serialNumber })
        assertEquals(SerialStatus.SOLD, result.updatedSerials.first { it.id == "s-1" }.status)
        assertEquals(SerialStatus.IN_STOCK, result.updatedSerials.first { it.id == "s-2" }.status)
        assertEquals(listOf("SN3", "SN9"), result.notFlippedNumbers)
        // The radio's SN1 is untouched: matching is per item.
        assertEquals(SerialStatus.IN_STOCK, result.updatedSerials.first { it.id == "s-4" }.status)
    }

    @Test
    fun markSerialsSoldPersistsOnlyFlippedRecords() = runBlocking {
        val vm = InventoryViewModel(FakeRepository())
        vm.addSerial(scope, "tv", "SN1")
        vm.addSerial(scope, "tv", "SN2")
        val result = vm.markSerialsSold(scope, "tv", listOf("SN1", "SN9"))
        assertEquals(1, result.flipped.size)
        assertEquals(listOf("SN9"), result.notFlippedNumbers)
        val all = vm.loadSerialsForItem(scope, "tv")
        assertEquals(SerialStatus.SOLD, all.first { it.serialNumber == "SN1" }.status)
        assertEquals(SerialStatus.IN_STOCK, all.first { it.serialNumber == "SN2" }.status)
    }

    @Test
    fun flipToStockReturnsOnlySoldSerials() {
        val serials = listOf(
            StockSerialRecord(id = "s-1", itemId = "tv", serialNumber = "SN1", status = SerialStatus.SOLD),
            StockSerialRecord(id = "s-2", itemId = "tv", serialNumber = "SN2"),
            StockSerialRecord(id = "s-3", itemId = "tv", serialNumber = "SN3", status = SerialStatus.REMOVED),
            StockSerialRecord(id = "s-4", itemId = "radio", serialNumber = "SN1", status = SerialStatus.SOLD),
        )
        val result = flipSerialsToStock(serials, "tv", listOf("SN1", "SN2", "SN3", "SN9"))
        assertEquals(listOf("SN1"), result.flipped.map { it.serialNumber })
        assertEquals(SerialStatus.IN_STOCK, result.updatedSerials.first { it.id == "s-1" }.status)
        assertEquals(SerialStatus.IN_STOCK, result.updatedSerials.first { it.id == "s-2" }.status)
        assertEquals(listOf("SN2", "SN3", "SN9"), result.notFlippedNumbers)
        // The radio's sold SN1 is untouched: matching is per item.
        assertEquals(SerialStatus.SOLD, result.updatedSerials.first { it.id == "s-4" }.status)
    }

    @Test
    fun markSerialsReturnedPersistsOnlyFlippedRecords() = runBlocking {
        val vm = InventoryViewModel(FakeRepository())
        vm.addSerial(scope, "tv", "SN1")
        vm.markSerialsSold(scope, "tv", listOf("SN1"))
        val result = vm.markSerialsReturned(scope, "tv", listOf("SN1", "SN9"))
        assertEquals(1, result.flipped.size)
        assertEquals(listOf("SN9"), result.notFlippedNumbers)
        val all = vm.loadSerialsForItem(scope, "tv")
        assertEquals(SerialStatus.IN_STOCK, all.first { it.serialNumber == "SN1" }.status)
    }
}
