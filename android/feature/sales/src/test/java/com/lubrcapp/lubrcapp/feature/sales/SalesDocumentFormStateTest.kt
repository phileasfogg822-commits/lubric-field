package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesDocumentFormStateTest {
    private val pkr = CurrencyCode.of("PKR")

    @Test
    fun aDraftBuildsALineWithItsTaxSnapshot() {
        val form = SalesDocumentFormState(
            type = SalesDocumentType.QUOTATION,
            date = "2026-10-23",
            lines = listOf(
                SalesLineDraft(
                    id = "l-1",
                    description = "Engine Oil",
                    quantity = "2",
                    rate = "500",
                    taxName = "Sales Tax",
                    taxPercent = "17",
                )
            ),
        )
        assertTrue(form.isValid)
        val lines = form.toLines("doc-1")
        assertEquals(1, lines.size)
        assertEquals(listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))), lines[0].taxComponents)
        assertEquals(BigDecimal("1170.00"), form.totalsOrNull(pkr)?.grandTotal?.amount)
    }

    @Test
    fun anUnparseableLineBlocksValidityAndTotals() {
        val form = SalesDocumentFormState(
            lines = listOf(SalesLineDraft(id = "l-1", quantity = "two", rate = "500")),
        )
        assertFalse(form.isValid)
        assertNull(form.totalsOrNull(pkr))
        assertTrue(form.toLines("doc-1").isEmpty())
    }

    @Test
    fun discountAndZeroTaxBehaviour() {
        val form = SalesDocumentFormState(
            lines = listOf(
                SalesLineDraft(
                    id = "l-1",
                    quantity = "1",
                    rate = "1000",
                    discountKind = "PERCENT",
                    discountValue = "10",
                    taxPercent = "0",
                )
            ),
        )
        val line = form.toLines("doc-1").single()
        assertEquals("PERCENT", line.discountKind)
        assertEquals(BigDecimal("10"), line.discountValue)
        assertTrue(line.taxComponents.isEmpty())
    }

    @Test
    fun fromDocumentRoundTripsThroughTheForm() {
        val document = SalesDocument(
            id = "doc-1",
            type = SalesDocumentType.SALES_ORDER,
            number = "SO-2026-00003",
            partyId = "party-1",
            date = "2026-10-23",
            dueDate = "2026-11-06",
            status = "CONFIRMED",
            notes = "Urgent",
        )
        val lines = listOf(
            SalesDocumentLine(
                id = "l-1",
                orderId = "doc-1",
                position = 0,
                description = "Shell HX7",
                quantity = BigDecimal("20"),
                rate = BigDecimal("6200"),
                taxComponents = listOf(SalesTaxComponent("GST", BigDecimal("18"))),
                fulfilledQuantity = BigDecimal("4"),
            )
        )
        val form = SalesDocumentFormState.fromDocument(document, lines)
        assertEquals(document, form.toDocument("doc-1"))
        assertEquals(lines, form.toLines("doc-1"))
    }
}
