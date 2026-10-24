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

    private val order = SalesDocument(
        id = "o-1",
        type = SalesDocumentType.SALES_ORDER,
        number = "SO-2026-00003",
        partyId = "party-1",
        date = "2026-10-21",
        dueDate = "2026-11-04",
        status = "CONFIRMED",
        notes = "Deliver before Friday",
    )

    private val orderLines = listOf(
        SalesDocumentLine(
            id = "ol-2", orderId = "o-1", position = 1,
            description = "Mobil 1 4L", quantity = BigDecimal("10"), rate = BigDecimal("8500"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            fulfilledQuantity = BigDecimal("4"),
        ),
        SalesDocumentLine(
            id = "ol-1", orderId = "o-1", position = 0,
            description = "Shell HX7 4L", quantity = BigDecimal("20"), rate = BigDecimal("6200"),
        ),
        SalesDocumentLine(
            id = "ol-x", orderId = "another-document", position = 0,
            description = "Not ours", quantity = BigDecimal("1"), rate = BigDecimal("1"),
        ),
    )

    @Test
    fun orderToInvoiceCopiesEverythingAndLinksBothWays() {
        val result = SalesDocumentConversion.salesOrderToInvoice(
            order,
            orderLines,
            newDocumentId = "inv-1",
            newLineId = { "new-" + it.id },
        )

        val invoice = result.invoice
        assertEquals(SalesDocumentType.INVOICE, invoice.type)
        assertEquals("DRAFT", invoice.status)
        assertEquals("", invoice.number)
        assertEquals("o-1", invoice.convertedFromId)
        assertNull(invoice.convertedToId)
        assertEquals("party-1", invoice.partyId)
        assertEquals("inv-1", result.convertedOrder.convertedToId)
        assertEquals("CONFIRMED", result.convertedOrder.status)
        assertEquals(listOf("new-ol-1", "new-ol-2"), result.invoiceLines.map { it.id })
        assertEquals(listOf("inv-1", "inv-1"), result.invoiceLines.map { it.orderId })
        assertEquals(BigDecimal.ZERO, result.invoiceLines[1].fulfilledQuantity)
        assertEquals(
            listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            result.invoiceLines[1].taxComponents,
        )
    }

    @Test
    fun onlyConfirmedOrFulfilledOrdersConvertAndNeverTwice() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.salesOrderToInvoice(
                order.copy(status = "DRAFT"),
                orderLines,
                newDocumentId = "inv-1",
                newLineId = { "new-" + it.id },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.salesOrderToInvoice(
                order.copy(status = "CANCELLED"),
                orderLines,
                newDocumentId = "inv-1",
                newLineId = { "new-" + it.id },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.salesOrderToInvoice(
                order.copy(convertedToId = "inv-0"),
                orderLines,
                newDocumentId = "inv-1",
                newLineId = { "new-" + it.id },
            )
        }
        // PARTIALLY_FULFILLED and FULFILLED convert too.
        SalesDocumentConversion.salesOrderToInvoice(
            order.copy(status = "PARTIALLY_FULFILLED"),
            orderLines,
            newDocumentId = "inv-1",
            newLineId = { "new-" + it.id },
        )
        SalesDocumentConversion.salesOrderToInvoice(
            order.copy(status = "FULFILLED"),
            orderLines,
            newDocumentId = "inv-1",
            newLineId = { "new-" + it.id },
        )
    }

    private val invoice = SalesDocument(
        id = "inv-1",
        type = SalesDocumentType.INVOICE,
        number = "INV-2026-00001",
        partyId = "party-1",
        date = "2026-10-24",
        status = "SENT",
        notes = "Deliver before Friday",
    )

    private val invoiceLines = listOf(
        SalesDocumentLine(
            id = "il-1", orderId = "inv-1", position = 0,
            description = "Shell HX7 4L", quantity = BigDecimal("20"), rate = BigDecimal("6200"),
        ),
        SalesDocumentLine(
            id = "il-2", orderId = "inv-1", position = 1,
            description = "Mobil 1 4L", quantity = BigDecimal("10"), rate = BigDecimal("8500"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            fulfilledQuantity = BigDecimal("10"),
        ),
    )

    @Test
    fun invoiceToReturnCopiesLinesAndLinksBackOnly() {
        val result = SalesDocumentConversion.invoiceToReturn(
            invoice,
            invoiceLines,
            newDocumentId = "ret-1",
            newLineId = { "new-" + it.id },
        )

        val returnDocument = result.returnDocument
        assertEquals(SalesDocumentType.SALES_RETURN, returnDocument.type)
        assertEquals("DRAFT", returnDocument.status)
        assertEquals("", returnDocument.number)
        assertEquals("inv-1", returnDocument.convertedFromId)
        assertNull(returnDocument.convertedToId)
        assertEquals("party-1", returnDocument.partyId)
        // No forward link on the invoice: many partial returns
        // may point at it, so no single link can name them.
        assertNull(invoice.convertedToId)
        assertEquals(listOf("new-il-1", "new-il-2"), result.returnLines.map { it.id })
        assertEquals(listOf("ret-1", "ret-1"), result.returnLines.map { it.orderId })
        assertEquals(BigDecimal.ZERO, result.returnLines[1].fulfilledQuantity)
        assertEquals(
            listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            result.returnLines[1].taxComponents,
        )
    }

    @Test
    fun onlyLiveInvoicesConvertToReturns() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.invoiceToReturn(
                invoice.copy(status = "DRAFT"),
                invoiceLines,
                newDocumentId = "ret-1",
                newLineId = { "new-" + it.id },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentConversion.invoiceToReturn(
                invoice.copy(status = "CANCELLED"),
                invoiceLines,
                newDocumentId = "ret-1",
                newLineId = { "new-" + it.id },
            )
        }
        for (status in listOf("PAID", "PARTIALLY_PAID", "OVERDUE")) {
            SalesDocumentConversion.invoiceToReturn(
                invoice.copy(status = status),
                invoiceLines,
                newDocumentId = "ret-1",
                newLineId = { "new-" + it.id },
            )
        }
    }
}
