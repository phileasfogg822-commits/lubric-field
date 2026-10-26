package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSalesSummaryMapperTest {

    private val pkr = CurrencyCode("PKR")
    private val today = "2026-10-27"

    private fun invoiceLines(orderId: String): List<SalesDocumentLine> = listOf(
        SalesDocumentLine(
            id = "l1",
            orderId = orderId,
            quantity = BigDecimal("2"),
            rate = BigDecimal("500"),
        ),
        SalesDocumentLine(
            id = "l2",
            orderId = orderId,
            quantity = BigDecimal("1"),
            rate = BigDecimal("300"),
        ),
    )

    @Test
    fun summarizeCountsTodaySalesAndOrdersOnly() {
        val documents = listOf(
            SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, date = today, status = "POSTED"),
            SalesDocument(id = "inv-2", type = SalesDocumentType.INVOICE, date = today, status = "DRAFT"),
            SalesDocument(id = "inv-3", type = SalesDocumentType.INVOICE, date = "2026-10-26", status = "POSTED"),
            SalesDocument(id = "so-1", type = SalesDocumentType.SALES_ORDER, date = today, status = "POSTED"),
            SalesDocument(id = "so-2", type = SalesDocumentType.SALES_ORDER, date = today, status = "DRAFT"),
        )
        val summary = FieldSalesSummaryMapper.summarize(
            documents,
            mapOf("inv-1" to invoiceLines("inv-1")),
            today,
            pkr,
        )
        assertEquals(Money.of("1300", pkr), summary.todaySales)
        assertEquals(1, summary.orders)
        assertNull(summary.collections)
        assertNull(summary.targetPercent)
    }

    @Test
    fun summarizeEmptyDayIsZeroNotNull() {
        val summary = FieldSalesSummaryMapper.summarize(emptyList(), emptyMap(), today, pkr)
        assertTrue(summary.todaySales == Money.zero(pkr))
        assertEquals(0, summary.orders)
    }

    @Test
    fun summarizeIgnoresUndatedDocuments() {
        val documents = listOf(
            SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, date = "", status = "POSTED"),
        )
        val summary = FieldSalesSummaryMapper.summarize(
            documents,
            mapOf("inv-1" to invoiceLines("inv-1")),
            today,
            pkr,
        )
        assertTrue(summary.todaySales == Money.zero(pkr))
        assertEquals(0, summary.orders)
    }
}
