package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.domain.inventory.LedgerMovement
import com.lubrcapp.lubrcapp.domain.inventory.StockLedger
import com.lubrcapp.lubrcapp.domain.inventory.StockLevels
import com.lubrcapp.lubrcapp.domain.inventory.StockStatus
import java.math.BigDecimal

/** One item's stock row on the §31 screen: balance and status derived, never stored. */
data class InventoryRow(
    val item: ItemStockOption,
    val balance: BigDecimal,
    val status: StockStatus,
    val needsReorder: Boolean,
    /** Balance × purchase rate; null when the item has no cost rate to value at. */
    val value: BigDecimal?,
)

/** The §31 screen model: the computed cards plus the rows they summarize. */
data class InventorySummary(
    val rows: List<InventoryRow>,
    /** §31 Inventory Value: Σ balance × cost rate over items that have a rate. Computed, never stored. */
    val totalValue: BigDecimal,
    val itemCount: Int,
    val lowStockCount: Int,
)

/**
 * Phase 19.3 — builds the §31 summary from the ledger. All
 * balances come from the 19.2 engine over the 19.1 movements;
 * the item's stored openingStock is the engine's starting
 * constant. With a warehouse filter, balances are that
 * warehouse's dimension (the opening constant belongs to the
 * warehouse-less dimension, so it does not leak into a single
 * warehouse's figure).
 */
object InventorySummaryBuilder {

    fun build(
        items: List<ItemStockOption>,
        movements: List<StockMovementRecord>,
        levelsByItem: Map<String, StockLevelsRecord>,
        warehouseId: String? = null,
    ): InventorySummary {
        val ledgerMovements = movements.map {
            LedgerMovement(itemId = it.itemId, warehouseId = it.warehouseId, batchId = it.batchId, quantity = it.quantity)
        }
        val rows = items.map { item ->
            val opening = item.openingStock ?: BigDecimal.ZERO
            val balance = if (warehouseId == null) {
                StockLedger.totalFor(ledgerMovements, item.id, opening)
            } else {
                StockLedger.balanceFor(ledgerMovements, item.id, warehouseId = warehouseId)
            }
            val levels = levelsByItem[item.id]?.let {
                StockLevels(minimum = it.minimum, reorderLevel = it.reorderLevel, maximum = it.maximum)
            } ?: StockLevels()
            InventoryRow(
                item = item,
                balance = balance,
                status = StockLedger.status(balance, levels),
                needsReorder = StockLedger.needsReorder(balance, levels),
                value = item.purchaseRate?.let { balance.multiply(it) },
            )
        }.sortedBy { it.item.name.lowercase() }
        return InventorySummary(
            rows = rows,
            totalValue = rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.value ?: BigDecimal.ZERO) },
            itemCount = rows.size,
            lowStockCount = rows.count { it.status == StockStatus.LOW_STOCK },
        )
    }
}
