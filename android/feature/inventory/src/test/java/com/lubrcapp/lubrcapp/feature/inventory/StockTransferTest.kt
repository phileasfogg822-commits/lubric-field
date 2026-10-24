package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.inventory.NegativeStockPolicy
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockTransferTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        fun seed(table: String, records: List<OperationalRecord>) {
            tables[table] = records.toMutableList()
        }
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

    private fun draftForm() = StockTransferFormState(
        fromWarehouseId = "wh-main",
        toWarehouseId = "wh-branch",
        date = "2026-10-24",
        note = "Weekly replenishment",
        quantityByItem = mapOf("oil" to "20"),
    )

    @Test
    fun codecRoundTripsHeaderAndLines() {
        val record = StockTransferRecord(
            id = "tr-1",
            fromWarehouseId = "wh-main",
            toWarehouseId = "wh-branch",
            status = TransferStatus.DISPATCHED,
            date = "2026-10-24",
            note = "Weekly replenishment",
            lines = listOf(
                StockTransferLine("oil", BigDecimal("20")),
                StockTransferLine("filter", BigDecimal("3.5")),
            ),
            createdAt = "2026-10-24T00:00:00Z",
        )
        val decoded = StockTransferJson.decode("other-id", StockTransferJson.encode(record))
        assertEquals("other-id", decoded.id)
        assertEquals("wh-main", decoded.fromWarehouseId)
        assertEquals("wh-branch", decoded.toWarehouseId)
        assertEquals(TransferStatus.DISPATCHED, decoded.status)
        assertEquals("2026-10-24", decoded.date)
        assertEquals(2, decoded.lines.size)
        assertEquals(0, BigDecimal("20").compareTo(decoded.lines[0].quantity))
        assertEquals(0, BigDecimal("3.5").compareTo(decoded.lines[1].quantity))
    }

    @Test
    fun codecToleratesGarbageAndUnknownStatus() {
        val garbage = StockTransferJson.decode("tr-9", "not json")
        assertEquals("tr-9", garbage.id)
        assertEquals(TransferStatus.DRAFT, garbage.status)
        assertTrue(garbage.lines.isEmpty())
        val unknown = StockTransferJson.decode(
            "tr-2",
            """{"fromWarehouseId":"a","toWarehouseId":"b","status":"LOST_IN_SPACE","lines":[{"itemId":"oil","quantity":"abc"}]}""",
        )
        assertEquals(TransferStatus.DRAFT, unknown.status)
        assertEquals(1, unknown.lines.size)
        assertEquals(0, BigDecimal.ZERO.compareTo(unknown.lines[0].quantity))
    }

    @Test
    fun formRequiresDifferentWarehousesAndAPositiveLine() {
        assertTrue(draftForm().isValid)
        assertTrue(!draftForm().copy(toWarehouseId = "wh-main").isValid)
        assertTrue(!draftForm().copy(fromWarehouseId = null).isValid)
        assertTrue(!draftForm().copy(quantityByItem = mapOf("oil" to "0")).isValid)
        assertTrue(!draftForm().copy(quantityByItem = mapOf("oil" to "abc")).isValid)
        assertTrue(!draftForm().copy(quantityByItem = emptyMap()).isValid)
    }

    @Test
    fun createSavesADraftAndRejectsAnInvalidForm() = runBlocking {
        val fake = FakeRepository()
        val vm = InventoryViewModel(fake)
        assertNull(vm.createTransfer(scope, draftForm().copy(toWarehouseId = "wh-main")))
        val created = vm.createTransfer(scope, draftForm())
        assertTrue(created != null)
        assertEquals(TransferStatus.DRAFT, created!!.status)
        assertEquals(listOf(created), vm.loadTransfers(scope))
        assertEquals(0, BigDecimal("20").compareTo(created.lines[0].quantity))
    }

    /** Seeds [atWarehouse] stock for oil via a PURCHASE movement, then creates a draft transfer. */
    private suspend fun seededTransfer(
        fake: FakeRepository,
        atWarehouse: String,
        stocked: String,
    ): Pair<InventoryViewModel, StockTransferRecord> {
        fake.seed("products", listOf(OperationalRecord("oil", """{"name":"Engine Oil"}""")))
        val vm = InventoryViewModel(fake)
        vm.saveMovement(
            scope,
            StockMovementRecord.of(
                id = "mv-in", itemId = "oil", type = MovementType.PURCHASE,
                quantity = BigDecimal(stocked), warehouseId = atWarehouse, date = "2026-10-24",
            ),
        )
        val created = vm.createTransfer(scope, draftForm())!!
        return vm to created
    }

    @Test
    fun dispatchPostsTransferOutAtSourceAndFlipsStatus() = runBlocking {
        val fake = FakeRepository()
        val (vm, created) = seededTransfer(fake, "wh-main", "50")
        val result = vm.dispatchTransfer(scope, created.id)
        assertTrue(result is TransferResult.Done)
        assertEquals(TransferStatus.DISPATCHED, (result as TransferResult.Done).transfer.status)
        val movements = vm.loadMovements(scope)
        assertEquals(2, movements.size)
        val out = movements.first { it.type == MovementType.TRANSFER_OUT }
        assertEquals(0, BigDecimal("-20").compareTo(out.quantity))
        assertEquals("wh-main", out.warehouseId)
        assertEquals("STOCK_TRANSFER", out.referenceType)
        assertEquals(created.id, out.referenceId)
        assertEquals(TransferStatus.DISPATCHED, vm.loadTransfers(scope)[0].status)
        // A second dispatch is not a valid state transition.
        assertEquals(TransferResult.InvalidState, vm.dispatchTransfer(scope, created.id))
    }

    @Test
    fun dispatchBlockedByNeverAllowPostsNothing() = runBlocking {
        val fake = FakeRepository()
        val (vm, created) = seededTransfer(fake, "wh-main", "10")
        val result = vm.dispatchTransfer(scope, created.id, NegativeStockPolicy.NEVER_ALLOW)
        assertEquals(TransferResult.BlockedByPolicy, result)
        // Only the seed PURCHASE movement exists; the transfer stays a draft.
        assertEquals(1, vm.loadMovements(scope).size)
        assertEquals(TransferStatus.DRAFT, vm.loadTransfers(scope)[0].status)
    }

    @Test
    fun receivePostsTransferInAtDestination() = runBlocking {
        val fake = FakeRepository()
        val (vm, created) = seededTransfer(fake, "wh-main", "50")
        assertEquals(TransferResult.InvalidState, vm.receiveTransfer(scope, created.id))
        vm.dispatchTransfer(scope, created.id)
        val result = vm.receiveTransfer(scope, created.id)
        assertTrue(result is TransferResult.Done)
        assertEquals(TransferStatus.RECEIVED, (result as TransferResult.Done).transfer.status)
        val inMovement = vm.loadMovements(scope).first { it.type == MovementType.TRANSFER_IN }
        assertEquals(0, BigDecimal("20").compareTo(inMovement.quantity))
        assertEquals("wh-branch", inMovement.warehouseId)
        assertEquals(created.id, inMovement.referenceId)
        assertEquals(TransferResult.InvalidState, vm.receiveTransfer(scope, created.id))
    }

    @Test
    fun cancelOnlyFromDraft() = runBlocking {
        val fake = FakeRepository()
        val (vm, created) = seededTransfer(fake, "wh-main", "50")
        val cancelled = vm.cancelTransfer(scope, created.id)
        assertTrue(cancelled is TransferResult.Done)
        assertEquals(TransferStatus.CANCELLED, vm.loadTransfers(scope)[0].status)

        val fake2 = FakeRepository()
        val (vm2, created2) = seededTransfer(fake2, "wh-main", "50")
        vm2.dispatchTransfer(scope, created2.id)
        assertEquals(TransferResult.InvalidState, vm2.cancelTransfer(scope, created2.id))
        assertEquals(TransferStatus.DISPATCHED, vm2.loadTransfers(scope)[0].status)
    }
}
