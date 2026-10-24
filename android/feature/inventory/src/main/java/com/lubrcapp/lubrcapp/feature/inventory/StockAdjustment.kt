package com.lubrcapp.lubrcapp.feature.inventory

import java.math.BigDecimal

/**
 * Phase 19.4 — the §36 adjustment kinds and the form state over
 * them. Each kind maps to a ledger [MovementType]; Manual
 * Correction is the one kind whose direction the user chooses.
 * §36 requires a reason — the form is invalid without one — and
 * allows an optional attachment, held as a URI reference (the
 * Phase 15 media posture: no binary in a record).
 */
enum class StockAdjustmentKind(val movementType: MovementType?) {
    OPENING_STOCK(MovementType.OPENING),
    STOCK_IN(MovementType.ADJUSTMENT_IN),
    STOCK_OUT(MovementType.ADJUSTMENT_OUT),
    DAMAGE(MovementType.ADJUSTMENT_OUT),
    EXPIRED(MovementType.ADJUSTMENT_OUT),
    LOST(MovementType.ADJUSTMENT_OUT),
    FOUND(MovementType.ADJUSTMENT_IN),
    MANUAL_CORRECTION(null),
}

data class StockAdjustmentFormState(
    val itemId: String? = null,
    val kind: StockAdjustmentKind = StockAdjustmentKind.STOCK_IN,
    /** Only used when [kind] is MANUAL_CORRECTION. */
    val manualInbound: Boolean = true,
    val quantityText: String = "",
    val warehouseId: String? = null,
    val batchId: String? = null,
    val date: String = "",
    val reason: String = "",
    val attachmentUri: String? = null,
) {
    val movementType: MovementType
        get() = kind.movementType
            ?: if (manualInbound) MovementType.ADJUSTMENT_IN else MovementType.ADJUSTMENT_OUT

    /** The unsigned quantity as entered; adjustments move a positive amount. */
    val quantity: BigDecimal?
        get() = quantityText.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                BigDecimal(it)
            } catch (e: Exception) {
                null
            }
        }

    val isValid: Boolean
        get() = itemId != null &&
            quantity != null && quantity!!.signum() > 0 &&
            reason.isNotBlank()
}

/** The outcome of posting an adjustment through the §175 policy check. */
sealed interface AdjustmentResult {
    data class Posted(val movement: StockMovementRecord, val warning: Boolean) : AdjustmentResult
    data object BlockedByPolicy : AdjustmentResult
}

/**
 * Phase 19.4 — one row of the §37 stock count: the expected
 * quantity is the ledger balance (derived, never stored), the
 * counted quantity is what was physically found, and the
 * variance between them is what "Adjust Stock" posts.
 */
data class StockCountRow(
    val item: ItemStockOption,
    val expected: BigDecimal,
    val countedText: String = "",
) {
    val counted: BigDecimal?
        get() = countedText.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                BigDecimal(it)
            } catch (e: Exception) {
                null
            }
        }

    /** counted − expected; null until a countable value is entered. */
    val variance: BigDecimal?
        get() = counted?.subtract(expected)

    /** The movement an adjustment would post; null when there is nothing to post. */
    val varianceMovementType: MovementType?
        get() = when {
            variance == null || variance!!.signum() == 0 -> null
            variance!!.signum() > 0 -> MovementType.ADJUSTMENT_IN
            else -> MovementType.ADJUSTMENT_OUT
        }
}
