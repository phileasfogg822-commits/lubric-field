package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.DocumentStatusRules
import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus

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
}
