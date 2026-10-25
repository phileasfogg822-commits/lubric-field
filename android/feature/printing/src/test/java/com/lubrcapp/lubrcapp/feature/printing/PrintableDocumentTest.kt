package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrintableDocumentTest {

    private fun line(description: String, quantity: String, rate: String, amount: String): PrintableLine =
        PrintableLine(description, BigDecimal(quantity), BigDecimal(rate), BigDecimal(amount))

    @Test
    fun totalsMath() {
        val document = printableDocument(
            title = "Invoice",
            documentNumber = "INV-1",
            date = "2026-10-25",
            partyName = "Acme",
            lines = listOf(
                line("Oil 1L", "2", "500", "1000"),
                line("Filter", "1", "300", "300"),
            ),
            taxTotal = BigDecimal("130"),
            discountTotal = BigDecimal("30"),
            currency = "PKR",
        )
        assertEquals(BigDecimal("1300"), document.subtotal)
        assertEquals(BigDecimal("1400"), document.grandTotal)
        assertEquals(BigDecimal("130"), document.taxTotal)
        assertEquals(BigDecimal("30"), document.discountTotal)
    }

    @Test
    fun emptyLinesProduceZeroTotals() {
        val document = printableDocument(
            title = "Receipt",
            documentNumber = "REC-1",
            date = "2026-10-25",
            lines = emptyList(),
        )
        assertEquals(BigDecimal.ZERO, document.subtotal)
        assertEquals(BigDecimal.ZERO, document.grandTotal)
        assertEquals("", document.currency)
    }

    @Test
    fun fieldsPassThrough() {
        val document = printableDocument(
            title = "Quotation",
            documentNumber = "QT-9",
            date = "2026-10-20",
            partyName = "Beta Traders",
            lines = listOf(line("Service", "1", "100", "100")),
            notes = "Valid 7 days",
            footer = "Thank you",
        )
        assertEquals("Quotation", document.title)
        assertEquals("QT-9", document.documentNumber)
        assertEquals("2026-10-20", document.date)
        assertEquals("Beta Traders", document.partyName)
        assertEquals("Valid 7 days", document.notes)
        assertEquals("Thank you", document.footer)
        assertEquals(BigDecimal("100"), document.subtotal)
    }

    @Test
    fun negativeTaxThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            printableDocument(
                title = "Invoice",
                documentNumber = "INV-1",
                date = "2026-10-25",
                lines = emptyList(),
                taxTotal = BigDecimal("-1"),
            )
        }
    }

    @Test
    fun negativeDiscountThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            printableDocument(
                title = "Invoice",
                documentNumber = "INV-1",
                date = "2026-10-25",
                lines = emptyList(),
                discountTotal = BigDecimal("-1"),
            )
        }
    }
}
