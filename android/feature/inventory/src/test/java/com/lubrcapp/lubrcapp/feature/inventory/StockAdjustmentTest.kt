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

class StockAdjustmentTest {

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

    @Test
    fun kindsMapToMovementTypes() {
        assertEquals(MovementType.OPENING, StockAdjustmentKind.OPENING_STOCK.movementType)
        assertEquals(MovementType.ADJUSTMENT_IN, StockAdjustmentKind.STOCK_IN.movementType)
        assertEquals(MovementType.ADJUSTMENT_IN, StockAdjustmentKind.FOUND.movementType)
        assertEquals(MovementType.ADJUSTMENT_OUT, StockAdjustmentKind.DAMAGE.movementType)
        assertEquals(MovementType.ADJUSTMENT_OUT, StockAdjustmentKind.EXPIRED.movementType)
        assertEquals(MovementType.ADJUSTMENT_OUT, StockAdjustmentKind.LOST.movementType)
        assertEquals(MovementType.ADJUSTMENT_OUT, StockAdjustmentKind.STOCK_OUT.movementType)
        assertNull(StockAdjustmentKind.MANUAL_CORRECTION.movementType)
        val manualOut = StockAdjustmentFormState(kind = StockAdjustmentKind.MANUAL_CORRECTION, manualInbound = false)
        assertEquals(MovementType.ADJUSTMENT_OUT, manualOut.movementType)
        val manualIn = StockAdjustmentFormState(kind = StockAdjustmentKind.MANUAL_CORRECTION, manualInbound = true)
        assertEquals(MovementType.ADJUSTMENT_IN, manualIn.movementType)
    }

    @Test
    fun formRequiresItemPositiveQuantityAndReason() {
        val base = StockAdjustmentFormState(itemId = "oil", quantityText = "3", reason = "Damage found")
        assertTrue(base.isValid)
        assertTrue(!base.copy(reason = " ").isValid)
        assertTrue(!base.copy(quantityText = "0").isValid)
        assertTrue(!base.copy(quantityText = "-2").isValid)
        assertTrue(!base.copy(quantityText = "abc").isValid)
        assertTrue(!base.copy(itemId = null).isValid)
    }

    @Test
    fun countVarianceMath() {
        val item = ItemStockOption(id = "oil", name = "Engine Oil")
        val row = StockCountRow(item = item, expected = BigDecimal("120"), countedText = "117")
        assertEquals(0, BigDecimal("-3").compareTo(row.variance!!))
        assertEquals(MovementType.ADJUSTMENT_OUT, row.varianceMovementType)
        val over = row.copy(countedText = "125")
        assertEquals(MovementType.ADJUSTMENT_IN, over.varianceMovementType)
        val exact = row.copy(countedText = "120")
        assertNull(exact.varianceMovementType)
        val blank = row.copy(countedText = "")
        assertNull(blank.variance)
    }

    @Test
    fun postAdjustmentPostsSignedMovementWithReason() = runBlocking {
        val fake = FakeRepository()
        fake.seed("products", listOf(OperationalRecord("oil", """{"name":"Engine Oil","openingStock":"50"}""")))
        val vm = InventoryViewModel(fake)
        val form = StockAdjustmentFormState(
            itemId = "oil", kind = StockAdjustmentKind.DAMAGE,
            quantityText = "3", reason = "Leaking cans", date = "2026-10-24",
        )
        val result = vm.postAdjustment(scope, form)
        assertTrue(result is AdjustmentResult.Posted)
        val movement = (result as AdjustmentResult.Posted).movement
        assertEquals(0, BigDecimal("-3").compareTo(movement.quantity))
        assertEquals("Leaking cans", movement.reason)
        assertEquals("STOCK_ADJUSTMENT", movement.referenceType)
        assertEquals(listOf(movement), vm.loadMovements(scope))
    }

    @Test
    fun outboundBeyondBalanceWarnsByDefaultAndBlocksWhenNeverAllow() = runBlocking {
        val fake = FakeRepository()
        fake.seed("products", listOf(OperationalRecord("oil", """{"name":"Engine Oil","openingStock":"5"}""")))
        val vm = InventoryViewModel(fake)
        val form = StockAdjustmentFormState(
            itemId = "oil", kind = StockAdjustmentKind.STOCK_OUT,
            quantityText = "8", reason = "Sent to branch",
        )
        val warned = vm.postAdjustment(scope, form)
        assertTrue(warned is AdjustmentResult.Posted && warned.warning)
        val blocked = vm.postAdjustment(scope, form, NegativeStockPolicy.NEVER_ALLOW)
        assertEquals(AdjustmentResult.BlockedByPolicy, blocked)
        // Only the warned posting landed.
        assertEquals(1, vm.loadMovements(scope).size)
    }

    @Test
    fun countVariancePostsAndZeroIsANoOp() = runBlocking {
        val fake = FakeRepository()
        fake.seed("products", listOf(OperationalRecord("oil", """{"name":"Engine Oil","openingStock":"120"}""")))
        val vm = InventoryViewModel(fake)
        assertNull(vm.postCountVariance(scope, "oil", BigDecimal.ZERO, reason = "Count"))
        val result = vm.postCountVariance(scope, "oil", BigDecimal("-3"), reason = "Stock count variance")
        assertTrue(result is AdjustmentResult.Posted)
        val movements = vm.loadMovements(scope)
        assertEquals(1, movements.size)
        assertEquals(0, BigDecimal("-3").compareTo(movements[0].quantity))
        assertEquals(MovementType.ADJUSTMENT_OUT, movements[0].type)
    }
}
