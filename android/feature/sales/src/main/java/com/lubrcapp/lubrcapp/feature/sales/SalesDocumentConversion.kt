package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesStatus
import java.math.BigDecimal

/**
 * Phase 18.5 — quotation → sales order conversion (§43: documents
 * flow into each other with no duplicate data entry). Pure: the
 * caller supplies the new ids and persists the three results
 * (the order, its lines, and the updated quotation) through the
 * ViewModel — orchestration lands with the 18.4 screens.
 *
 * Only an ACCEPTED quotation converts — that is the single legal
 * path into CONVERTED in the 18.2 lifecycle. The order starts as
 * an unnumbered DRAFT (18.3 numbers it on first save), copies
 * party, dates, notes and every line including its tax snapshot,
 * and starts fulfilment from zero: fulfilment is the order's own
 * history, never inherited from the quotation. The links are
 * written both ways (§43 traceability).
 *
 * Phase 21.2 adds the same shape for sales order → invoice
 * (§43, Convert-to-Invoice deferred from 18): only a CONFIRMED,
 * PARTIALLY_FULFILLED, or FULFILLED order converts — a DRAFT
 * order is not yet a commitment and a CANCELLED order is dead —
 * and an order that already names an invoice (convertedToId)
 * never converts twice, because the single link cannot name two
 * invoices (multi-invoice orders are a later phase's problem).
 * The invoice starts as an unnumbered DRAFT, copies party,
 * dates, notes and every line including its tax snapshot, and
 * starts fulfilment from zero: delivery against the invoice is
 * the invoice's own history, never inherited from the order.
 * Unlike the quotation, the order keeps its status — the order
 * lifecycle has no INVOICED state, and invoicing does not
 * consume the order — but it gains the forward link.
 */
object SalesDocumentConversion {

    data class Result(
        val order: SalesDocument,
        val orderLines: List<SalesDocumentLine>,
        val convertedQuotation: SalesDocument,
    )

    fun quotationToSalesOrder(
        quotation: SalesDocument,
        lines: List<SalesDocumentLine>,
        newDocumentId: String,
        newLineId: (SalesDocumentLine) -> String,
    ): Result {
        require(quotation.type == SalesDocumentType.QUOTATION) { "Not a quotation: ${quotation.id}" }
        val converted = SalesDocumentStatus.transition(quotation, QuotationStatus.CONVERTED)
            .copy(convertedToId = newDocumentId)
        val order = SalesDocument(
            id = newDocumentId,
            type = SalesDocumentType.SALES_ORDER,
            number = "",
            partyId = quotation.partyId,
            date = quotation.date,
            dueDate = quotation.dueDate,
            status = "DRAFT",
            notes = quotation.notes,
            convertedFromId = quotation.id,
            convertedToId = null,
        )
        val orderLines = lines
            .filter { it.orderId == quotation.id }
            .sortedBy { it.position }
            .map { line ->
                line.copy(
                    id = newLineId(line),
                    orderId = newDocumentId,
                    fulfilledQuantity = BigDecimal.ZERO,
                )
            }
        return Result(order = order, orderLines = orderLines, convertedQuotation = converted)
    }

    data class InvoiceResult(
        val invoice: SalesDocument,
        val invoiceLines: List<SalesDocumentLine>,
        val convertedOrder: SalesDocument,
    )

    fun salesOrderToInvoice(
        order: SalesDocument,
        lines: List<SalesDocumentLine>,
        newDocumentId: String,
        newLineId: (SalesDocumentLine) -> String,
    ): InvoiceResult {
        require(order.type == SalesDocumentType.SALES_ORDER) { "Not a sales order: ${order.id}" }
        val status = SalesDocumentStatus.orderStatus(order)
        require(
            status == SalesOrderStatus.CONFIRMED ||
                status == SalesOrderStatus.PARTIALLY_FULFILLED ||
                status == SalesOrderStatus.FULFILLED
        ) { "Order in $status cannot convert to an invoice: ${order.id}" }
        require(order.convertedToId == null) { "Order already converted to ${order.convertedToId}: ${order.id}" }
        val convertedOrder = order.copy(convertedToId = newDocumentId)
        val invoice = SalesDocument(
            id = newDocumentId,
            type = SalesDocumentType.INVOICE,
            number = "",
            partyId = order.partyId,
            date = order.date,
            dueDate = order.dueDate,
            status = "DRAFT",
            notes = order.notes,
            convertedFromId = order.id,
            convertedToId = null,
        )
        val invoiceLines = lines
            .filter { it.orderId == order.id }
            .sortedBy { it.position }
            .map { line ->
                line.copy(
                    id = newLineId(line),
                    orderId = newDocumentId,
                    fulfilledQuantity = BigDecimal.ZERO,
                )
            }
        return InvoiceResult(invoice = invoice, invoiceLines = invoiceLines, convertedOrder = convertedOrder)
    }

    data class ReturnResult(
        val returnDocument: SalesDocument,
        val returnLines: List<SalesDocumentLine>,
    )

    /**
     * Phase 21.4 — invoice → sales return (§64: select the
     * original invoice). Only a SENT, PAID, PARTIALLY_PAID, or
     * OVERDUE invoice converts: a DRAFT invoice sold nothing
     * yet and a CANCELLED invoice is dead. The return starts
     * as an unnumbered DRAFT, copies party, dates, notes, and
     * every line including its tax snapshot, and starts
     * fulfilment from zero. Deliberately unlike 21.2's invoice
     * conversion, the invoice gains NO forward link and keeps
     * its status: an invoice takes many partial returns over
     * time, so no single link can name them, and the return
     * reduces the receivable through its posting (21.4), not
     * through the invoice's status. The return's reason goes
     * in notes at the screen; the conversion copies the
     * invoice's notes as the starting point, the 21.2 pattern.
     */
    fun invoiceToReturn(
        invoice: SalesDocument,
        lines: List<SalesDocumentLine>,
        newDocumentId: String,
        newLineId: (SalesDocumentLine) -> String,
    ): ReturnResult {
        require(invoice.type == SalesDocumentType.INVOICE) { "Not an invoice: ${invoice.id}" }
        val status = SalesDocumentStatus.invoiceStatus(invoice)
        require(
            status == SalesStatus.SENT || status == SalesStatus.PAID ||
                status == SalesStatus.PARTIALLY_PAID || status == SalesStatus.OVERDUE
        ) { "Invoice in $status cannot convert to a return: ${invoice.id}" }
        val returnDocument = SalesDocument(
            id = newDocumentId,
            type = SalesDocumentType.SALES_RETURN,
            number = "",
            partyId = invoice.partyId,
            date = invoice.date,
            dueDate = invoice.dueDate,
            status = "DRAFT",
            notes = invoice.notes,
            convertedFromId = invoice.id,
            convertedToId = null,
        )
        val returnLines = lines
            .filter { it.orderId == invoice.id }
            .sortedBy { it.position }
            .map { line ->
                line.copy(
                    id = newLineId(line),
                    orderId = newDocumentId,
                    fulfilledQuantity = BigDecimal.ZERO,
                )
            }
        return ReturnResult(returnDocument = returnDocument, returnLines = returnLines)
    }
}
