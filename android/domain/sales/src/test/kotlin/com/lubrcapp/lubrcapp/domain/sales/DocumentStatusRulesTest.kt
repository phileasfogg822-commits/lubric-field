package com.lubrcapp.lubrcapp.domain.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStatusRulesTest {

    @Test
    fun quotationHappyPathIsLegal() {
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.DRAFT, QuotationStatus.SENT))
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.SENT, QuotationStatus.VIEWED))
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.VIEWED, QuotationStatus.ACCEPTED))
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.ACCEPTED, QuotationStatus.CONVERTED))
    }

    @Test
    fun quotationRejectionAndExpiryAreReachableBeforeAcceptanceOnly() {
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.SENT, QuotationStatus.REJECTED))
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.VIEWED, QuotationStatus.EXPIRED))
        assertTrue(DocumentStatusRules.canTransition(QuotationStatus.DRAFT, QuotationStatus.EXPIRED))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.ACCEPTED, QuotationStatus.REJECTED))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.ACCEPTED, QuotationStatus.EXPIRED))
    }

    @Test
    fun quotationTerminalStatesAndSkipsAreIllegal() {
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.DRAFT, QuotationStatus.ACCEPTED))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.DRAFT, QuotationStatus.CONVERTED))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.CONVERTED, QuotationStatus.DRAFT))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.REJECTED, QuotationStatus.SENT))
        assertFalse(DocumentStatusRules.canTransition(QuotationStatus.SENT, QuotationStatus.SENT))
        assertTrue(DocumentStatusRules.isTerminal(QuotationStatus.CONVERTED))
        assertTrue(DocumentStatusRules.isTerminal(QuotationStatus.REJECTED))
        assertFalse(DocumentStatusRules.isTerminal(QuotationStatus.SENT))
    }

    @Test
    fun salesOrderLifecycleIsLegal() {
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.DRAFT, SalesOrderStatus.CONFIRMED))
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED))
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.FULFILLED))
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.CONFIRMED, SalesOrderStatus.FULFILLED))
    }

    @Test
    fun salesOrderCancellationAndTerminalRules() {
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.DRAFT, SalesOrderStatus.CANCELLED))
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.CONFIRMED, SalesOrderStatus.CANCELLED))
        assertTrue(DocumentStatusRules.canTransition(SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.CANCELLED))
        assertFalse(DocumentStatusRules.canTransition(SalesOrderStatus.FULFILLED, SalesOrderStatus.CANCELLED))
        assertFalse(DocumentStatusRules.canTransition(SalesOrderStatus.CANCELLED, SalesOrderStatus.CONFIRMED))
        assertFalse(DocumentStatusRules.canTransition(SalesOrderStatus.DRAFT, SalesOrderStatus.FULFILLED))
        assertTrue(DocumentStatusRules.isTerminal(SalesOrderStatus.FULFILLED))
        assertTrue(DocumentStatusRules.isTerminal(SalesOrderStatus.CANCELLED))
    }

    @Test
    fun fulfilmentDerivationFollowsTheSpecExample() {
        // §44: order Shell HX7 20 + Mobil 1 10; delivered 20 + 4 → partial.
        val partial = DocumentStatusRules.fulfilmentStatus(
            listOf(BigDecimal("20") to BigDecimal("20"), BigDecimal("10") to BigDecimal("4"))
        )
        assertEquals(SalesOrderStatus.PARTIALLY_FULFILLED, partial)

        val complete = DocumentStatusRules.fulfilmentStatus(
            listOf(BigDecimal("20") to BigDecimal("20"), BigDecimal("10") to BigDecimal("10"))
        )
        assertEquals(SalesOrderStatus.FULFILLED, complete)

        val untouched = DocumentStatusRules.fulfilmentStatus(
            listOf(BigDecimal("20") to BigDecimal.ZERO, BigDecimal("10") to BigDecimal.ZERO)
        )
        assertNull(untouched)
        assertNull(DocumentStatusRules.fulfilmentStatus(emptyList()))
    }
}
