package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SalesDocumentConversionTest {

    private val quotation = SalesDocument(
        id = "q-1",
        type = SalesDocumentType.QUOTATION,
        number = "QT-2026-00007",
        partyId = "party-1",
        date = "2026-10-20",
        dueDate = "2026-11-03",
        status = "ACCEPTED",
        notes = "Validity 14 days",
    )

    private val lines = listOf(
        SalesDocumentLine(
            id = "l-2", orderId = "q-1", position = 1,
            description = "Mobil 1 4L", quantity = BigDecimal("10"), rate = BigDecimal("8500"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            fulfilledQuantity = BigDecimal("3"),
        ),
        SalesDocumentLine(
            id = "l-1", orderId = "q-1", position = 0,
            description = "Shell HX7 4L", quantity = BigDecimal("20"), rate = BigDecimal("6200"),
        ),
        SalesDocumentLine(
            id = "l-x", orderId = "another-document", position = 0,
            description = "Not ours", quantity = BigDecimal("1"), rate = BigDecimal("1"),
        ),
    )

    @Test
    fun conversionCopiesEverythingAndLinksBothWays() {
        val result = SalesDocumentConversion.quotationToSalesOrder(
            quotation,
            lines,
            newDocumentId = "o-1",
            newLineId = { "new-" + it.id },
        )

        val order = result.order
        assertEquals(SalesDocumentType.SALES_ORDER, order.type)
        assertEquals("DRAFT", order.status)
        assertEquals("", order.number)
        assertEquals("party-1", order.partyId)
        assertEquals("2026-10-20", order.date)
        assertEquals("2026-11-03", order.dueDate)
        assertEquals("Validity 14 days", order.notes)
        assertEquals("q-1", order.convertedFromId)
        assertNull(order.convertedToId)

        assertEquals("CONVERTED", result.convertedQuotation.status)
        assertEquals("o-1", result.convertedQuotation.convertedToId)

        // Only the quotation's own lines, in position order, with
        // fresh ids, the same commercial content, and fulfilment
        // reset to zero.
        assertEquals(listOf("new-l-1", "new-l-2"), result.orderLines.map { it.id })
        assertEquals(listOf("o-1", "o-1"), result.orderLines.map { it.orderId })
        assertEquals(BigDecimal("6200"), result.orderLines[0].rate)
        assertEquals(
            listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            result.orderLines[1].taxComponents,
        )
        assertEquals(BigDecimal.ZERO, result.orderLines[1].fulfilledQuantity)
    }

    @Test
    fun onlyAnAcceptedQuotationConverts() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.quotationToSalesOrder(
                quotation.copy(status = "SENT"),
                lines,
                newDocumentId = "o-1",
                newLineId = { "new-" + it.id },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.quotationToSalesOrder(
                quotation.copy(type = SalesDocumentType.SALES_ORDER, status = "CONFIRMED"),
                lines,
                newDocumentId = "o-1",
                newLineId = { "new-" + it.id },
            )
        }
    }
}
