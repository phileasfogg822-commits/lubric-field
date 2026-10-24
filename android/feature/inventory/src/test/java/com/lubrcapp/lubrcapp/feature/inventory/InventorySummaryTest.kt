package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.domain.inventory.StockStatus
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventorySummaryTest {

    private fun assertQty(expected: String, actual: BigDecimal?) {
        assertTrue("expected $expected but was $actual", actual != null && BigDecimal(expected).compareTo(actual) == 0)
    }

    private val oil = ItemStockOption(
        id = "oil", name = "Engine Oil", purchaseRate = BigDecimal("100"), openingStock = BigDecimal("50"),
    )
    private val filter = ItemStockOption(id = "filter", name = "Oil Filter")

    private fun sale(itemId: String, qty: String, warehouse: String? = null) =
        StockMovementRecord.of(
            id = "mv-$itemId-$qty-${warehouse ?: "none"}",
            itemId = itemId, type = MovementType.SALE, quantity = BigDecimal(qty),
            warehouseId = warehouse, date = "2026-10-24",
        )

    @Test
    fun balancesComeFromMovementsPlusTheOpeningConstant() {
        val summary = InventorySummaryBuilder.build(
            items = listOf(oil, filter),
            movements = listOf(sale("oil", "5")),
            levelsByItem = emptyMap(),
        )
        val oilRow = summary.rows.first { it.item.id == "oil" }
        assertQty("45", oilRow.balance)
        assertEquals(StockStatus.IN_STOCK, oilRow.status)
        assertQty("4500", oilRow.value)
        assertQty("4500", summary.totalValue)
        assertEquals(2, summary.itemCount)
        // The filter has no opening and no movements: out of stock, no value.
        val filterRow = summary.rows.first { it.item.id == "filter" }
        assertEquals(StockStatus.OUT_OF_STOCK, filterRow.status)
        assertNull(filterRow.value)
    }

    @Test
    fun warehouseFilterUsesThatWarehousesDimensionOnly() {
        val movements = listOf(sale("oil", "5", warehouse = "wh-1"), sale("oil", "3", warehouse = "wh-2"))
        val all = InventorySummaryBuilder.build(listOf(oil), movements, emptyMap())
        assertQty("42", all.rows[0].balance)
        val wh1 = InventorySummaryBuilder.build(listOf(oil), movements, emptyMap(), warehouseId = "wh-1")
        // The opening constant belongs to the warehouse-less dimension.
        assertQty("-5", wh1.rows[0].balance)
    }

    @Test
    fun levelsDriveLowStockAndReorder() {
        val levels = mapOf("oil" to StockLevelsRecord(itemId = "oil", minimum = BigDecimal("48"), reorderLevel = BigDecimal("60")))
        val summary = InventorySummaryBuilder.build(listOf(oil), listOf(sale("oil", "5")), levels)
        val row = summary.rows[0]
        assertEquals(StockStatus.LOW_STOCK, row.status)
        assertTrue(row.needsReorder)
        assertEquals(1, summary.lowStockCount)
    }

    @Test
    fun levelsCodecRoundTripsAndToleratesGarbage() {
        val record = StockLevelsRecord(itemId = "oil", minimum = BigDecimal("10"), reorderLevel = BigDecimal("30"))
        val decoded = StockLevelsJson.decode("oil", StockLevelsJson.encode(record))
        assertEquals(record, decoded)
        val garbage = StockLevelsJson.decode("oil", "nope")
        assertEquals(StockLevelsRecord(itemId = "oil"), garbage)
    }

    @Test
    fun referenceDataReadsItemFieldsTolerantly() {
        val records = listOf(
            OperationalRecord("p1", """{"name":"Engine Oil","purchaseRate":"100","openingStock":"50","categoryId":"c1"}"""),
            OperationalRecord("p2", """{"purchaseRate":"not-a-number"}"""),
        )
        val options = InventoryReferenceData.itemOptions(records)
        assertEquals("Engine Oil", options[0].name)
        assertQty("100", options[0].purchaseRate)
        assertQty("50", options[0].openingStock)
        assertEquals("c1", options[0].categoryId)
        // Unnamed record falls back to its id; unparseable rate is null.
        assertEquals("p2", options[1].name)
        assertNull(options[1].purchaseRate)
    }
}
