package com.lubrcapp.lubrcapp.domain.inventory

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockLedgerTest {

    private fun mv(item: String, qty: String, warehouse: String? = null, batch: String? = null) =
        LedgerMovement(itemId = item, warehouseId = warehouse, batchId = batch, quantity = BigDecimal(qty))

    private fun assertQty(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun spec180ExampleDerivesTheBalance() {
        // §180: Opening +100, Purchase +20, Sale −5, Return +1, Adjustment −2 → 114.
        val movements = listOf(
            mv("oil", "100"), mv("oil", "20"), mv("oil", "-5"), mv("oil", "1"), mv("oil", "-2"),
        )
        assertQty("114", StockLedger.totalFor(movements, "oil"))
        val balances = StockLedger.balances(movements)
        assertEquals(1, balances.size)
        assertQty("114", balances[0].quantity)
    }

    @Test
    fun openingConstantIsTheStartingPointNotAMovement() {
        val movements = listOf(mv("oil", "-5"))
        assertQty("45", StockLedger.totalFor(movements, "oil", opening = BigDecimal("50")))
        assertQty("45", StockLedger.balanceFor(movements, "oil", opening = BigDecimal("50")))
        // The opening constant lands on the warehouse-less dimension only.
        assertQty("0", StockLedger.balanceFor(movements, "oil", warehouseId = "wh-1", opening = BigDecimal("50")))
    }

    @Test
    fun balancesSplitByWarehouseAndBatch() {
        val movements = listOf(
            mv("oil", "10", warehouse = "wh-1"),
            mv("oil", "4", warehouse = "wh-2"),
            mv("oil", "3", warehouse = "wh-1", batch = "b-1"),
            mv("filter", "9"),
        )
        val balances = StockLedger.balances(movements)
        assertEquals(4, balances.size)
        assertQty("10", StockLedger.balanceFor(movements, "oil", warehouseId = "wh-1"))
        assertQty("4", StockLedger.balanceFor(movements, "oil", warehouseId = "wh-2"))
        assertQty("3", StockLedger.balanceFor(movements, "oil", warehouseId = "wh-1", batchId = "b-1"))
        assertQty("17", StockLedger.totalFor(movements, "oil"))
    }

    @Test
    fun statusDerivationFollows32() {
        val levels = StockLevels(minimum = BigDecimal("10"), reorderLevel = BigDecimal("15"), maximum = BigDecimal("500"))
        assertEquals(StockStatus.OUT_OF_STOCK, StockLedger.status(BigDecimal.ZERO, levels))
        assertEquals(StockStatus.OUT_OF_STOCK, StockLedger.status(BigDecimal("-3"), levels))
        assertEquals(StockStatus.LOW_STOCK, StockLedger.status(BigDecimal("10"), levels))
        assertEquals(StockStatus.IN_STOCK, StockLedger.status(BigDecimal("11"), levels))
        assertEquals(StockStatus.OVERSTOCK, StockLedger.status(BigDecimal("501"), levels))
        assertEquals(StockStatus.INACTIVE, StockLedger.status(BigDecimal("100"), levels, isActive = false))
        // No levels set: any positive balance is simply in stock.
        assertEquals(StockStatus.IN_STOCK, StockLedger.status(BigDecimal("1"), StockLevels()))
    }

    @Test
    fun reorderThresholdFallsBackToMinimum() {
        assertTrue(StockLedger.needsReorder(BigDecimal("15"), StockLevels(minimum = BigDecimal("10"), reorderLevel = BigDecimal("15"))))
        assertTrue(!StockLedger.needsReorder(BigDecimal("16"), StockLevels(minimum = BigDecimal("10"), reorderLevel = BigDecimal("15"))))
        assertTrue(StockLedger.needsReorder(BigDecimal("10"), StockLevels(minimum = BigDecimal("10"))))
        assertTrue(!StockLedger.needsReorder(BigDecimal("0"), StockLevels()))
    }

    @Test
    fun negativeStockPolicyIsAnInput() {
        val balance = BigDecimal("5")
        val outgoing = BigDecimal("8")
        assertEquals(NegativeStockDecision.BLOCKED, StockLedger.checkNegative(NegativeStockPolicy.NEVER_ALLOW, balance, outgoing))
        assertEquals(NegativeStockDecision.ALLOWED_WITH_WARNING, StockLedger.checkNegative(NegativeStockPolicy.WARN, balance, outgoing))
        assertEquals(NegativeStockDecision.ALLOWED, StockLedger.checkNegative(NegativeStockPolicy.ALLOW, balance, outgoing))
        // Within balance, every policy allows.
        assertEquals(NegativeStockDecision.ALLOWED, StockLedger.checkNegative(NegativeStockPolicy.NEVER_ALLOW, balance, BigDecimal("5")))
    }
}
