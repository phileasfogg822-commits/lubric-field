package com.lubrcapp.lubrcapp.feature.inventory

import com.lubrcapp.lubrcapp.domain.accounting.StockMovement
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.WarehouseId
import java.math.BigDecimal

/**
 * Phase 19.1 — the typed stock movement: one entry in the §180
 * inventory ledger, stored as a "stock_movements" record. That
 * table is in the Phase 12 sync whitelist AND is INSERT_ONLY in
 * the sync queue, so the ledger is append-only by platform rule
 * (§181): a movement is never edited or deleted — a correction
 * is a new reversing movement, written by the flows that own it
 * (19.4 adjustments, 19.5 transfers, Phase 21 document posting).
 *
 * The quantity is SIGNED, the ledger convention of §180
 * (Opening +100, Sale −5): inbound types are positive, outbound
 * types negative. [MovementType.isInbound] states each type's
 * direction so writers never guess a sign.
 *
 * This record is the feature-layer form of the Phase 17.4
 * posting contract's vocabulary: [toPostingMovement] converts to
 * domain/accounting's [StockMovement] (positive quantity,
 * direction carried by kind), so the two layers cannot drift.
 */
enum class MovementType {
    OPENING,
    PURCHASE,
    SALE,
    RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    TRANSFER_IN,
    TRANSFER_OUT,
    ;

    /** Inbound types add stock; outbound types remove it. */
    val isInbound: Boolean
        get() = when (this) {
            OPENING, PURCHASE, RETURN, ADJUSTMENT_IN, TRANSFER_IN -> true
            SALE, ADJUSTMENT_OUT, TRANSFER_OUT -> false
        }
}

data class StockMovementRecord(
    val id: String,
    val itemId: String,
    val warehouseId: String? = null,
    val batchId: String? = null,
    val type: MovementType,
    /** Signed: positive adds stock, negative removes it. */
    val quantity: BigDecimal,
    /** Unit rate as a plain amount string in storage; currency is the caller's context. */
    val rate: BigDecimal? = null,
    /** The document that caused this movement (e.g. "SALES_ORDER"), when one did. */
    val referenceType: String? = null,
    val referenceId: String? = null,
    /** ISO date text as entered (yyyy-MM-dd); no calendar math in this layer. */
    val date: String = "",
    /** Why this movement exists (§36 requires one for adjustments). */
    val reason: String = "",
    /** Optional §36 attachment as a URI reference (Phase 15 media posture). */
    val attachmentUri: String? = null,
    /** ISO instant the movement was recorded. */
    val createdAt: String = "",
) {
    /** Direction in the 17.4 vocabulary, derived from the type — never stored separately. */
    val kind: StockMovementKind
        get() = if (type.isInbound) StockMovementKind.IN else StockMovementKind.OUT

    /** True when the stored sign disagrees with the type's direction (never written by this layer's writers). */
    val hasSignMismatch: Boolean
        get() = quantity.signum() != 0 && (quantity.signum() > 0) != type.isInbound

    /**
     * The 17.4 posting form of this movement: positive quantity,
     * direction by kind. [currency] is the caller's because a
     * ledger rate only has meaning in a business's currency.
     */
    fun toPostingMovement(currency: CurrencyCode): StockMovement = StockMovement(
        itemId = ItemId(itemId),
        warehouseId = warehouseId?.let { WarehouseId(it) },
        kind = kind,
        quantity = Quantity.of(quantity.abs()),
        rate = rate?.let { Money.of(it, currency) },
        batchId = batchId,
    )

    companion object {
        /**
         * Build a movement from an unsigned [quantity] and its
         * [type]; the sign is applied from the type's direction
         * so callers cannot write a SALE with a positive quantity.
         */
        fun of(
            id: String,
            itemId: String,
            type: MovementType,
            quantity: BigDecimal,
            warehouseId: String? = null,
            batchId: String? = null,
            rate: BigDecimal? = null,
            referenceType: String? = null,
            referenceId: String? = null,
            date: String = "",
            reason: String = "",
            attachmentUri: String? = null,
            createdAt: String = "",
        ): StockMovementRecord {
            val magnitude = quantity.abs()
            return StockMovementRecord(
                id = id,
                itemId = itemId,
                warehouseId = warehouseId,
                batchId = batchId,
                type = type,
                quantity = if (type.isInbound) magnitude else magnitude.negate(),
                rate = rate,
                referenceType = referenceType,
                referenceId = referenceId,
                date = date,
                reason = reason,
                attachmentUri = attachmentUri,
                createdAt = createdAt,
            )
        }
    }
}
