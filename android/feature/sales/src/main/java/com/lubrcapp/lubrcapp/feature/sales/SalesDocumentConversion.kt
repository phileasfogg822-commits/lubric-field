package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
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
}
