package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesTaxComponent
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintingPreviewMapperTest {

    private fun invoice(
        id: String,
        date: String,
        number: String = "INV-$id",
        notes: String = "",
    ): SalesDocument = SalesDocument(
        id = id,
        type = SalesDocumentType.INVOICE,
        number = number,
        date = date,
        notes = notes,
    )

    private fun line(
        id: String,
        description: String,
        quantity: String,
        rate: String,
        position: Int = 0,
        discountKind: String? = null,
        discountValue: String? = null,
        taxComponents: List<SalesTaxComponent> = emptyList(),
        taxInclusive: Boolean = false,
    ): SalesDocumentLine = SalesDocumentLine(
        id = id,
        orderId = "inv-1",
        position = position,
        description = description,
        quantity = BigDecimal(quantity),
        rate = BigDecimal(rate),
        discountKind = discountKind,
        discountValue = discountValue?.let { BigDecimal(it) },
        taxComponents = taxComponents,
        taxInclusive = taxInclusive,
    )

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue(
            "expected $expected but was $actual",
            actual.compareTo(BigDecimal(expected)) == 0,
        )
    }

    @Test
    fun latestInvoicePicksGreatestDateAndBreaksTiesById() {
        val invoices = listOf(
            invoice("a", "2026-10-26"),
            invoice("old", "2026-10-20"),
            invoice("b", "2026-10-26"),
        )
        assertEquals("b", latestInvoice(invoices)?.id)
    }

    @Test
    fun latestInvoiceReturnsNullOnEmpty() {
        assertNull(latestInvoice(emptyList()))
    }

    @Test
    fun invoiceToPrintableMapsEngineAmounts() {
        val document = invoiceToPrintable(
            invoice("inv-1", "2026-10-26", number = "INV-1", notes = ""),
            listOf(
                line("l1", "Oil", "2", "100", position = 0),
                line("l2", "Filter", "1", "50", position = 1, discountKind = "PERCENT", discountValue = "10"),
                line(
                    "l3", "Grease", "1", "100", position = 2,
                    taxComponents = listOf(SalesTaxComponent("Tax", BigDecimal("10"))),
                ),
            ),
            partyName = "Ali",
        )
        assertEquals("INV-1", document.documentNumber)
        assertEquals("2026-10-26", document.date)
        assertEquals("Ali", document.partyName)
        assertNull(document.notes)
        // Line amounts are the engine's taxable bases (200, 45, 100),
        // not its tax-inclusive totals — otherwise the builder's
        // subtotal + tax would double-count the tax.
        assertMoney("200", document.lines[0].amount)
        assertMoney("45", document.lines[1].amount)
        assertMoney("100", document.lines[2].amount)
        assertMoney("345", document.subtotal)
        assertMoney("10", document.taxTotal)
        assertMoney("0", document.discountTotal)
        assertMoney("355", document.grandTotal)
    }

    @Test
    fun inclusiveTaxLineUsesExtractedBase() {
        val document = invoiceToPrintable(
            invoice("inv-1", "2026-10-26"),
            listOf(
                line(
                    "l1", "Oil", "1", "110", position = 0,
                    taxComponents = listOf(SalesTaxComponent("Tax", BigDecimal("10"))),
                    taxInclusive = true,
                ),
            ),
            partyName = null,
        )
        assertMoney("100", document.lines[0].amount)
        assertMoney("100", document.subtotal)
        assertMoney("10", document.taxTotal)
        assertMoney("110", document.grandTotal)
    }

    @Test
    fun nonBlankNotesAreKept() {
        val document = invoiceToPrintable(
            invoice("inv-1", "2026-10-26", notes = "Call first"),
            emptyList(),
            partyName = null,
        )
        assertEquals("Call first", document.notes)
    }
}
