package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.QuotationStatus
import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SalesDocumentStatusTest {

    private fun document(type: SalesDocumentType, status: String) =
        SalesDocument(id = "doc-1", type = type, status = status)

    private fun line(quantity: String, fulfilled: String) = SalesDocumentLine(
        id = "line",
        orderId = "doc-1",
        quantity = BigDecimal(quantity),
        rate = BigDecimal("100"),
        fulfilledQuantity = BigDecimal(fulfilled),
    )

    @Test
    fun legalTransitionsMoveTheStoredStatus() {
        val sent = SalesDocumentStatus.transition(
            document(SalesDocumentType.QUOTATION, "DRAFT"),
            QuotationStatus.SENT,
        )
        assertEquals("SENT", sent.status)

        val confirmed = SalesDocumentStatus.transition(
            document(SalesDocumentType.SALES_ORDER, "DRAFT"),
            SalesOrderStatus.CONFIRMED,
        )
        assertEquals("CONFIRMED", confirmed.status)
    }

    @Test
    fun illegalTransitionsAndWrongDocumentKindsThrow() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.transition(
                document(SalesDocumentType.QUOTATION, "DRAFT"),
                QuotationStatus.CONVERTED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.transition(
                document(SalesDocumentType.SALES_ORDER, "DRAFT"),
                QuotationStatus.SENT,
            )
        }
    }

    @Test
    fun anUnknownStoredStatusReadsAsDraft() {
        assertEquals(
            QuotationStatus.DRAFT,
            SalesDocumentStatus.quotationStatus(document(SalesDocumentType.QUOTATION, "LEGACY")),
        )
        assertEquals(
            SalesOrderStatus.DRAFT,
            SalesDocumentStatus.orderStatus(document(SalesDocumentType.SALES_ORDER, "")),
        )
    }

    @Test
    fun fulfilmentMovesAConfirmedOrderAndLeavesAnUntouchedOneAlone() {
        val order = document(SalesDocumentType.SALES_ORDER, "CONFIRMED")
        val partial = SalesDocumentStatus.applyFulfilment(
            order,
            listOf(line("20", "20"), line("10", "4")),
        )
        assertEquals("PARTIALLY_FULFILLED", partial.status)

        val complete = SalesDocumentStatus.applyFulfilment(
            partial,
            listOf(line("20", "20"), line("10", "10")),
        )
        assertEquals("FULFILLED", complete.status)

        val untouched = SalesDocumentStatus.applyFulfilment(order, listOf(line("20", "0")))
        assertEquals("CONFIRMED", untouched.status)
    }

    @Test
    fun fulfilmentRefusesToMoveACancelledOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.applyFulfilment(
                document(SalesDocumentType.SALES_ORDER, "CANCELLED"),
                listOf(line("20", "20")),
            )
        }
    }
}
