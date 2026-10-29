package com.lubrcapp.lubrcapp.feature.printing

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemedPrintTest {

    private fun invoice(): PrintableDocument = printableDocument(
        title = "Invoice",
        documentNumber = "INV-1",
        date = "2026-10-18",
        partyName = "Ali Ahmed",
        lines = listOf(
            PrintableLine("Basmati Rice 5kg", BigDecimal("2"), BigDecimal("1650"), BigDecimal("3300")),
            PrintableLine("Fresh Milk 1L", BigDecimal("5"), BigDecimal("220"), BigDecimal("1100")),
        ),
        taxTotal = BigDecimal("100"),
        discountTotal = BigDecimal.ZERO,
        currency = "PKR",
    )

    @Test
    fun themesAreFiveDistinct() {
        assertEquals(5, PRINT_THEMES.size)
        assertEquals(5, PRINT_THEMES.map { it.id }.toSet().size)
        assertEquals(5, PRINT_THEMES.map { it.argb }.toSet().size)
        assertEquals(PRINT_THEMES.first(), printThemeById("missing-id"))
    }

    @Test
    fun initialsCases() {
        assertEquals("AA", initialsFor("Ali Ahmed"))
        assertEquals("FK", initialsFor("Fatima Khan"))
        assertEquals("S", initialsFor("Sample"))
        assertEquals("?", initialsFor(""))
        assertEquals("?", initialsFor("   "))
        assertEquals("US", initialsFor("United Suppliers (Pvt) Ltd"))
    }

    @Test
    fun invoiceLayoutShape() {
        val rows = layoutThemedInvoice(invoice())
        val header = rows.filterIsInstance<ThemedRow.TableHeader>().single()
        assertEquals(listOf("Item", "Qty", "Rate", "Amount"), header.columns)
        assertEquals(2, rows.filterIsInstance<ThemedRow.TableRow>().size)
        val totals = rows.filterIsInstance<ThemedRow.Total>()
        assertEquals(listOf("Subtotal", "Tax", "Total"), totals.map { it.label })
        assertEquals("4400 PKR", totals.first { it.label == "Subtotal" }.value)
        assertEquals("4500 PKR", totals.first { it.label == "Total" }.value)
        assertTrue(totals.first { it.label == "Total" }.emphasized)
    }

    @Test
    fun zeroTaxAndDiscountRowsOmitted() {
        val rows = layoutThemedInvoice(
            printableDocument(
                title = "Invoice",
                documentNumber = "INV-2",
                date = "2026-10-18",
                lines = listOf(
                    PrintableLine("Soap", BigDecimal("1"), BigDecimal("130"), BigDecimal("130")),
                ),
            ),
        )
        assertEquals(
            listOf("Subtotal", "Total"),
            rows.filterIsInstance<ThemedRow.Total>().map { it.label },
        )
    }

    @Test
    fun statementSplitAndRunningBalances() {
        val rows = layoutThemedStatement(
            partyName = "Ali Ahmed",
            currency = "PKR",
            entries = listOf(
                StatementEntryInput("2026-10-18", "INV-1", BigDecimal("4400"), null, BigDecimal("4400")),
                StatementEntryInput("2026-10-27", "REC-1", null, BigDecimal("2000"), BigDecimal("2400")),
            ),
            closingBalance = BigDecimal("2400"),
        )
        val header = rows.filterIsInstance<ThemedRow.TableHeader>().single()
        assertEquals(listOf("Date", "Detail", "Debit", "Credit", "Balance"), header.columns)
        val items = rows.filterIsInstance<ThemedRow.TableRow>()
        assertEquals(listOf("2026-10-18", "INV-1", "4400", "", "4400"), items[0].cells)
        assertEquals(listOf("2026-10-27", "REC-1", "", "2000", "2400"), items[1].cells)
        val closing = rows.filterIsInstance<ThemedRow.Total>().single()
        assertEquals("Closing balance", closing.label)
        assertEquals("2400 PKR", closing.value)
        assertTrue(closing.emphasized)
    }
}
