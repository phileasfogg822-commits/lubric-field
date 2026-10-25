package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLayoutTest {

    private fun document(
        lines: List<PrintableLine> = emptyList(),
        taxTotal: BigDecimal = BigDecimal.ZERO,
        discountTotal: BigDecimal = BigDecimal.ZERO,
        partyName: String? = "Acme",
        notes: String? = null,
        footer: String? = null,
    ): PrintableDocument = printableDocument(
        title = "Invoice",
        documentNumber = "INV-1",
        date = "2026-10-25",
        partyName = partyName,
        lines = lines,
        taxTotal = taxTotal,
        discountTotal = discountTotal,
        currency = "PKR",
        notes = notes,
        footer = footer,
    )

    private fun line(description: String, amount: String): PrintableLine =
        PrintableLine(description, BigDecimal.ONE, BigDecimal(amount), BigDecimal(amount))

    private fun allTexts(layout: DocumentLayout): List<String> =
        layout.pages.flatMap { page -> page.lines.map { it.text } }

    @Test
    fun contentOrderAndTotals() {
        val layout = layoutDocument(
            document(
                lines = listOf(line("Oil 1L", "1000"), line("Filter", "300")),
                taxTotal = BigDecimal("130"),
                discountTotal = BigDecimal("30"),
            ),
        )
        assertEquals(
            listOf(
                "Invoice",
                "No: INV-1    Date: 2026-10-25",
                "Party: Acme",
                "",
                "Oil 1L  1 x 1000 = 1000",
                "Filter  1 x 300 = 300",
                "Subtotal: 1300",
                "Tax: 130",
                "Discount: 30",
                "Total: 1400",
            ),
            allTexts(layout),
        )
        val totalLine = layout.pages.flatMap { it.lines }.first { it.text.startsWith("Total:") }
        assertTrue(totalLine.bold)
        val titleLine = layout.pages[0].lines[0]
        assertEquals(LayoutAlign.CENTER, titleLine.align)
    }

    @Test
    fun zeroTaxAndDiscountLinesAreOmitted() {
        val layout = layoutDocument(document(lines = listOf(line("Item", "500"))))
        val texts = allTexts(layout)
        assertTrue(texts.none { it.startsWith("Tax:") })
        assertTrue(texts.none { it.startsWith("Discount:") })
        assertTrue(texts.contains("Subtotal: 500"))
        assertTrue(texts.contains("Total: 500"))
    }

    @Test
    fun absentPartyProducesNoPartyLine() {
        val layout = layoutDocument(document(partyName = null))
        assertTrue(allTexts(layout).none { it.startsWith("Party:") })
    }

    @Test
    fun notesAndFooterFollowTheTotal() {
        val layout = layoutDocument(document(notes = "Valid 7 days", footer = "Thank you"))
        val texts = allTexts(layout)
        assertTrue(texts.indexOf("Total: 0") < texts.indexOf("Notes: Valid 7 days"))
        assertEquals("Notes: Valid 7 days", texts[texts.size - 2])
        assertEquals("Thank you", texts[texts.size - 1])
    }

    @Test
    fun longDocumentsPaginatePreservingOrder() {
        val items = (1..3).map { line("Item $it", "100") }
        val layout = layoutDocument(
            document = document(lines = items),
            pageHeight = 100f,
            marginTop = 10f,
            marginBottom = 10f,
        )
        assertTrue(layout.pages.size == 2)
        val firstPageTexts = layout.pages[0].lines.map { it.text }
        assertEquals("Invoice", firstPageTexts[0])
        val secondPageTexts = layout.pages[1].lines.map { it.text }
        assertTrue(secondPageTexts[0].startsWith("Item"))
        assertEquals(items.size + 6, allTexts(layout).size)
    }
}
