package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.DocumentStatusRules
import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus
import java.math.BigDecimal

/**
 * Phase 18.2 — the bridge between a document's stored status text
 * (18.1) and the typed lifecycle rules in domain/sales. Parsing is
 * tolerant in the codec tradition: an unknown stored value reads
 * as DRAFT rather than crashing the list. Transitions go through
 * [DocumentStatusRules] and refuse illegal moves — a caller that
 * wants to offer only legal actions reads the same rules.
 */
object SalesDocumentStatus {

    fun quotationStatus(document: SalesDocument): QuotationStatus =
        QuotationStatus.entries.firstOrNull { it.name == document.status } ?: QuotationStatus.DRAFT

    fun orderStatus(document: SalesDocument): SalesOrderStatus =
        SalesOrderStatus.entries.firstOrNull { it.name == document.status } ?: SalesOrderStatus.DRAFT

    /** The document with its status moved, or throws when the move is illegal. */
    fun transition(document: SalesDocument, target: QuotationStatus): SalesDocument {
        require(document.type == SalesDocumentType.QUOTATION) { "Not a quotation: ${document.id}" }
        val from = quotationStatus(document)
        require(DocumentStatusRules.canTransition(from, target)) {
            "Illegal quotation transition $from -> $target"
        }
        return document.copy(status = target.name)
    }

    /** The document with its status moved, or throws when the move is illegal. */
    fun transition(document: SalesDocument, target: SalesOrderStatus): SalesDocument {
        require(document.type == SalesDocumentType.SALES_ORDER) { "Not a sales order: ${document.id}" }
        val from = orderStatus(document)
        require(DocumentStatusRules.canTransition(from, target)) {
            "Illegal sales order transition $from -> $target"
        }
        return document.copy(status = target.name)
    }

    /**
     * Re-derive an order's status from its lines' fulfilment
     * (§44). Returns the document unchanged when nothing has been
     * fulfilled, and refuses to move an order whose current status
     * cannot legally reach the derived one (e.g. a cancelled
     * order) — that state is a data problem, not a transition.
     */
    fun applyFulfilment(document: SalesDocument, lines: List<SalesDocumentLine>): SalesDocument {
        require(document.type == SalesDocumentType.SALES_ORDER) { "Not a sales order: ${document.id}" }
        val derived = DocumentStatusRules.fulfilmentStatus(
            lines.map { it.quantity to it.fulfilledQuantity }
        ) ?: return document
        val from = orderStatus(document)
        if (from == derived) return document
        require(DocumentStatusRules.canTransition(from, derived)) {
            "Order in $from cannot move to derived fulfilment status $derived"
        }
        return document.copy(status = derived.name)
    }

    /**
     * Phase 21.2 — record a delivery against one order line: the
     * action 18.2's derivation was missing. The line's fulfilled
     * quantity grows (never shrinks — a return is 21.4's job, not
     * a negative delivery), and the order's status re-derives
     * through [applyFulfilment]. Only a CONFIRMED,
     * PARTIALLY_FULFILLED, or FULFILLED order takes deliveries:
     * a DRAFT order is not yet a commitment and a CANCELLED order
     * is dead, and the derivation itself refuses DRAFT →
     * PARTIALLY_FULFILLED, so the gate is stated here rather
     * than surfacing as a transition error. Over-delivery is
     * not an error (§44: fulfilled >= ordered completes the
     * line). Recording a delivery posts no stock movement —
     * movements post at invoice time (21.3), never twice.
     */
    data class DeliveryResult(
        val document: SalesDocument,
        val lines: List<SalesDocumentLine>,
    )

    fun recordDelivery(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        lineId: String,
        additionalQuantity: BigDecimal,
    ): DeliveryResult {
        require(document.type == SalesDocumentType.SALES_ORDER) { "Not a sales order: ${document.id}" }
        val from = orderStatus(document)
        require(
            from == SalesOrderStatus.CONFIRMED ||
                from == SalesOrderStatus.PARTIALLY_FULFILLED ||
                from == SalesOrderStatus.FULFILLED
        ) { "Order in $from cannot take a delivery: ${document.id}" }
        require(additionalQuantity.signum() > 0) { "Delivery quantity must be positive: $additionalQuantity" }
        val target = lines.firstOrNull { it.id == lineId }
            ?: throw NoSuchElementException("No line $lineId on order ${document.id}")
        val updatedLines = lines.map { line ->
            if (line.id == lineId) line.copy(fulfilledQuantity = line.fulfilledQuantity + additionalQuantity)
            else line
        }
        return DeliveryResult(document = applyFulfilment(document, updatedLines), lines = updatedLines)
    }
}
