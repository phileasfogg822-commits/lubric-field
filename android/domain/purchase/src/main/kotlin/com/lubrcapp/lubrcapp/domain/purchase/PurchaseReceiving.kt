package com.lubrcapp.lubrcapp.domain.purchase

import com.lubrcapp.lubrcapp.domain.accounting.StockMovement
import com.lubrcapp.lubrcapp.domain.accounting.StockMovementKind
import com.lubrcapp.lubrcapp.domain.common.Quantity
import java.math.BigDecimal

/**
 * Phase 23.3 — goods receipt: the purchase-status
 * transition (supervisor decision 3). Receiving is
 * NOT a separate record — it moves a DRAFT purchase
 * to RECEIVED on the same record, adjusts the lines
 * to what actually arrived, and emits stock IN
 * movements against those lines.
 *
 * Short and over delivery are line adjustments on
 * the same record: a line's quantity becomes the
 * received quantity, whatever it is. A line absent
 * from the received map counts as zero received —
 * its quantity becomes zero and it emits no
 * movement (a movement must carry a positive
 * quantity by the posting contract). Entries for
 * line ids not on the invoice are ignored; the
 * returned lines and movements show exactly what
 * was applied, so nothing is silently assumed.
 *
 * Only a DRAFT purchase can be received. A second
 * receipt of the same purchase is rejected rather
 * than merged — multi-shipment receipt is a later
 * concern, not a silent re-adjustment.
 *
 * The returned invoice carries the RECEIVED status;
 * its stored money totals are NOT recomputed here —
 * totals are recomputed from lines at posting time
 * (the 23.2 rule), so a stale stored total can never
 * corrupt the journal. Purchase lines carry no
 * warehouse, so movements carry no warehouse id;
 * the inventory join assigns one when it persists
 * them. This function moves no money and posts no
 * journal — that is 23.2's job, separately.
 */
data class ReceivingResult(
    val invoice: PurchaseInvoice,
    val lines: List<PurchaseInvoiceLine>,
    val movements: List<StockMovement>,
)

object PurchaseReceiving {

    fun receive(
        invoice: PurchaseInvoice,
        lines: List<PurchaseInvoiceLine>,
        received: Map<String, Quantity>,
    ): ReceivingResult {
        require(invoice.status == PurchaseStatus.DRAFT) {
            "Only a DRAFT purchase can be received: ${invoice.id.value} is ${invoice.status}"
        }
        require(received.values.all { it.amount.signum() >= 0 }) {
            "Received quantities must not be negative: ${invoice.id.value}"
        }
        val adjustedLines = lines.map { line ->
            val got = received[line.id.value] ?: Quantity.of(BigDecimal.ZERO)
            line.copy(quantity = got)
        }
        val movements = lines.mapNotNull { line ->
            val got = received[line.id.value] ?: Quantity.of(BigDecimal.ZERO)
            if (got.amount.signum() <= 0) return@mapNotNull null
            StockMovement(
                itemId = line.itemId,
                warehouseId = null,
                kind = StockMovementKind.IN,
                quantity = got,
                rate = line.unitPrice,
            )
        }
        return ReceivingResult(
            invoice = invoice.copy(status = PurchaseStatus.RECEIVED),
            lines = adjustedLines,
            movements = movements,
        )
    }
}
