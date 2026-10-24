package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryMovementViewModelTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeMovementRepository : OperationalBusinessRepository {
        val rows = mutableListOf<OperationalRecord>()
        val tables = mutableListOf<String>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
            tables.add(table)
            return flowOf(rows.toList())
        }

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
            tables.add(table)
            return rows.toList()
        }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            tables.add(table)
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    @Test
    fun saveAndLoadMovementRoundTrips() = runBlocking {
        val fake = FakeMovementRepository()
        val vm = InventoryViewModel(fake)
        val movement = StockMovementRecord.of(
            id = "mv-1", itemId = "item-1", type = MovementType.OPENING,
            quantity = BigDecimal("100"), warehouseId = "wh-1", date = "2026-10-01",
        )
        vm.saveMovement(scope, movement)
        val loaded = vm.loadMovements(scope)
        assertEquals(listOf(movement), loaded)
        assertTrue(fake.tables.all { it == "stock_movements" })
    }

    @Test
    fun postMovementSignsStampsAndAppends() = runBlocking {
        val fake = FakeMovementRepository()
        val vm = InventoryViewModel(fake)
        val posted = vm.postMovement(
            scope = scope, itemId = "item-1", type = MovementType.SALE,
            quantity = BigDecimal("5"), referenceType = "SALES_ORDER",
            referenceId = "order-1", date = "2026-10-24",
        )
        assertEquals(0, BigDecimal("-5").compareTo(posted.quantity))
        assertTrue(posted.id.isNotBlank())
        assertTrue(posted.createdAt.isNotBlank())
        assertEquals(listOf(posted), vm.loadMovements(scope))
    }

    @Test
    fun loadMovementsForItemFiltersAndSortsByDate() = runBlocking {
        val fake = FakeMovementRepository()
        val vm = InventoryViewModel(fake)
        vm.saveMovement(scope, StockMovementRecord.of(id = "b", itemId = "item-1", type = MovementType.SALE, quantity = BigDecimal("5"), date = "2026-10-20"))
        vm.saveMovement(scope, StockMovementRecord.of(id = "a", itemId = "item-1", type = MovementType.OPENING, quantity = BigDecimal("100"), date = "2026-10-01"))
        vm.saveMovement(scope, StockMovementRecord.of(id = "c", itemId = "item-2", type = MovementType.OPENING, quantity = BigDecimal("7"), date = "2026-10-02"))
        val forItem = vm.loadMovementsForItem(scope, "item-1")
        assertEquals(listOf("a", "b"), forItem.map { it.id })
    }
}
