package com.lubrcapp.lubrcapp.feature.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SalesDocumentFulfilmentTest {

    private val order = SalesDocument(
        id = "o-1",
        type = SalesDocumentType.SALES_ORDER,
        number = "SO-2026-00003",
        status = "CONFIRMED",
    )

    private fun lines() = listOf(
        SalesDocumentLine(id = "l-1", orderId = "o-1", position = 0, quantity = BigDecimal("10"), rate = BigDecimal("100")),
        SalesDocumentLine(id = "l-2", orderId = "o-1", position = 1, quantity = BigDecimal("5"), rate = BigDecimal("200")),
    )

    @Test
    fun partialDeliveryDerivesPartiallyFulfilled() {
        val result = SalesDocumentStatus.recordDelivery(order, lines(), "l-1", BigDecimal("4"))
        assertEquals(BigDecimal("4"), result.lines.first { it.id == "l-1" }.fulfilledQuantity)
        assertEquals("PARTIALLY_FULFILLED", result.document.status)
    }

    @Test
    fun completingEveryLineDerivesFulfilled() {
        val first = SalesDocumentStatus.recordDelivery(order, lines(), "l-1", BigDecimal("10"))
        val second = SalesDocumentStatus.recordDelivery(first.document, first.lines, "l-2", BigDecimal("5"))
        assertEquals("FULFILLED", second.document.status)
    }

    @Test
    fun overDeliveryCompletesTheLineWithoutError() {
        val result = SalesDocumentStatus.recordDelivery(order, lines(), "l-1", BigDecimal("12"))
        assertEquals(BigDecimal("12"), result.lines.first { it.id == "l-1" }.fulfilledQuantity)
        assertEquals("PARTIALLY_FULFILLED", result.document.status)
    }

    @Test
    fun deliveryRefusesDraftCancelledZeroNegativeAndUnknownLine() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.recordDelivery(order.copy(status = "DRAFT"), lines(), "l-1", BigDecimal("1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.recordDelivery(order.copy(status = "CANCELLED"), lines(), "l-1", BigDecimal("1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.recordDelivery(order, lines(), "l-1", BigDecimal.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.recordDelivery(order, lines(), "l-1", BigDecimal("-1"))
        }
        assertThrows(NoSuchElementException::class.java) {
            SalesDocumentStatus.recordDelivery(order, lines(), "l-9", BigDecimal("1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesDocumentStatus.recordDelivery(
                order.copy(type = SalesDocumentType.INVOICE),
                lines(),
                "l-1",
                BigDecimal("1"),
            )
        }
    }
}
