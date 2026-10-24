package com.lubrcapp.lubrcapp.domain.inventory

import java.math.BigDecimal

/**
 * Phase 19.2 — the §180 inventory ledger engine (pure rules).
 *
 * Stock is DERIVED from movements, never read from a stored
 * quantity: a balance is the item's opening constant plus the
 * sum of its signed movement quantities. The opening constant
 * is the Phase 15 `openingStock` item datum, supplied by the
 * caller per item — the engine never fabricates an OPENING
 * movement (fake history), and the Phase 16 stored batch
 * quantity is never summed in: batch balances derive only from
 * movements carrying that batchId.
 *
 * Dimensions: every balance is per (item, warehouse, batch).
 * A movement with no warehouse counts toward the item's
 * warehouse-less balance only; the item total is the sum over
 * all of its balances (see [StockLedger.totalFor]).
 */

/** One signed ledger entry, the domain form of a stock movement. */
data class LedgerMovement(
    val itemId: String,
    val warehouseId: String? = null,
    val batchId: String? = null,
    /** Signed: positive adds stock, negative removes it. */
    val quantity: BigDecimal,
)

data class StockBalance(
    val itemId: String,
    val warehouseId: String? = null,
    val batchId: String? = null,
    val quantity: BigDecimal,
)

/** §32 stock status. */
enum class StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK,
    OVERSTOCK,
    INACTIVE,
}

/** §33 per-item stock levels; any level may be unset. */
data class StockLevels(
    val minimum: BigDecimal? = null,
    val reorderLevel: BigDecimal? = null,
    val maximum: BigDecimal? = null,
)

/** §175 negative-stock policy — a business setting, always an input here. */
enum class NegativeStockPolicy {
    NEVER_ALLOW,
    WARN,
    ALLOW,
}

/** The outcome of a §175 check on a proposed outgoing movement. */
enum class NegativeStockDecision {
    /** The movement keeps the balance at or above zero. */
    ALLOWED,

    /** The movement takes the balance negative and the policy only warns. */
    ALLOWED_WITH_WARNING,

    /** The movement takes the balance negative and the policy forbids it. */
    BLOCKED,
}

object StockLedger {

    /**
     * All balances implied by [movements], grouped by
     * (item, warehouse, batch). [openingStock] supplies the
     * per-item starting constant, applied to the item's
     * warehouse-less, batch-less balance — the dimension the
     * Phase 15 datum describes. Items whose only presence is
     * an opening constant still appear (with that balance).
     */
    fun balances(
        movements: List<LedgerMovement>,
        openingStock: Map<String, BigDecimal> = emptyMap(),
    ): List<StockBalance> {
        val sums = LinkedHashMap<Triple<String, String?, String?>, BigDecimal>()
        for ((itemId, opening) in openingStock) {
            if (opening.signum() != 0) {
                sums[Triple(itemId, null, null)] = opening
            }
        }
        for (movement in movements) {
            val key = Triple(movement.itemId, movement.warehouseId, movement.batchId)
            sums[key] = (sums[key] ?: BigDecimal.ZERO).add(movement.quantity)
        }
        return sums.map { (key, quantity) ->
            StockBalance(itemId = key.first, warehouseId = key.second, batchId = key.third, quantity = quantity)
        }.sortedWith(compareBy({ it.itemId }, { it.warehouseId ?: "" }, { it.batchId ?: "" }))
    }

    /** The balance of one exact (item, warehouse, batch) dimension. */
    fun balanceFor(
        movements: List<LedgerMovement>,
        itemId: String,
        warehouseId: String? = null,
        batchId: String? = null,
        opening: BigDecimal = BigDecimal.ZERO,
    ): BigDecimal {
        var total = if (warehouseId == null && batchId == null) opening else BigDecimal.ZERO
        for (movement in movements) {
            if (movement.itemId == itemId && movement.warehouseId == warehouseId && movement.batchId == batchId) {
                total = total.add(movement.quantity)
            }
        }
        return total
    }

    /** The item's total across every warehouse and batch, opening included. */
    fun totalFor(
        movements: List<LedgerMovement>,
        itemId: String,
        opening: BigDecimal = BigDecimal.ZERO,
    ): BigDecimal {
        var total = opening
        for (movement in movements) {
            if (movement.itemId == itemId) total = total.add(movement.quantity)
        }
        return total
    }

    /**
     * §32 status for a balance. An inactive item is INACTIVE
     * whatever it holds. Otherwise: at or below zero is OUT_OF_STOCK;
     * at or below the §33 minimum is LOW_STOCK; above the maximum
     * (when one is set) is OVERSTOCK; anything else is IN_STOCK.
     */
    fun status(balance: BigDecimal, levels: StockLevels, isActive: Boolean = true): StockStatus {
        if (!isActive) return StockStatus.INACTIVE
        if (balance.signum() <= 0) return StockStatus.OUT_OF_STOCK
        levels.maximum?.let { if (balance.compareTo(it) > 0) return StockStatus.OVERSTOCK }
        levels.minimum?.let { if (balance.compareTo(it) <= 0) return StockStatus.LOW_STOCK }
        return StockStatus.IN_STOCK
    }

    /** §33: reorder is due at or below the reorder level (the minimum when no reorder level is set). */
    fun needsReorder(balance: BigDecimal, levels: StockLevels): Boolean {
        val threshold = levels.reorderLevel ?: levels.minimum ?: return false
        return balance.compareTo(threshold) <= 0
    }

    /**
     * §175 check for a proposed outgoing [outgoing] quantity
     * (given unsigned) against the [currentBalance] of the
     * exact dimension it would leave from.
     */
    fun checkNegative(
        policy: NegativeStockPolicy,
        currentBalance: BigDecimal,
        outgoing: BigDecimal,
    ): NegativeStockDecision {
        val resulting = currentBalance.subtract(outgoing.abs())
        if (resulting.signum() >= 0) return NegativeStockDecision.ALLOWED
        return when (policy) {
            NegativeStockPolicy.NEVER_ALLOW -> NegativeStockDecision.BLOCKED
            NegativeStockPolicy.WARN -> NegativeStockDecision.ALLOWED_WITH_WARNING
            NegativeStockPolicy.ALLOW -> NegativeStockDecision.ALLOWED
        }
    }
}
