package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEvaluatorsTest {

    private val today = "2026-10-27"
    private fun money(amount: String, currency: String = "PKR") =
        Money.of(amount, CurrencyCode(currency))

    @Test
    fun lowStockFiresOnlyBelowThreshold() {
        val levels = listOf(
            StockLevel("i1", "Mobil 1", BigDecimal("4"), BigDecimal("5")),
            StockLevel("i2", "Shell HX7", BigDecimal("5"), BigDecimal("5")),
            StockLevel("i3", "Castrol", BigDecimal("9"), BigDecimal("5")),
        )
        val notes = AutomationEvaluators.lowStock(levels, today)
        assertEquals(listOf("low-i1"), notes.map { it.id })
        assertEquals(NotificationType.LOW_STOCK, notes.single().type)
        assertEquals("Only 4 left", notes.single().detail)
    }

    @Test
    fun overdueInvoicesFireOnlyWhenUnpaidAndPastDue() {
        val invoices = listOf(
            OverdueInvoiceInput("INV-1", "Ali Autos", "2026-10-10", paid = false),
            OverdueInvoiceInput("INV-2", "Bilal Traders", "2026-10-27", paid = false),
            OverdueInvoiceInput("INV-3", "Crown Motors", "2026-10-01", paid = true),
        )
        val notes = AutomationEvaluators.overdueInvoices(invoices, today)
        assertEquals(listOf("overdue-INV-1"), notes.map { it.id })
        assertEquals(NotificationType.PAYMENT_OVERDUE, notes.single().type)
    }

    @Test
    fun expiringStockFiresForTodayOrPastExpiry() {
        val items = listOf(
            ExpiringStockInput("i1", "Oil A", "2026-10-27"),
            ExpiringStockInput("i2", "Oil B", "2026-10-20"),
            ExpiringStockInput("i3", "Oil C", "2026-11-01"),
        )
        val notes = AutomationEvaluators.expiringStock(items, today)
        assertEquals(listOf("expiry-i1", "expiry-i2"), notes.map { it.id })
    }

    @Test
    fun purchasesDueFireOnlyWhenUnreceivedAndDateReached() {
        val orders = listOf(
            PurchaseDueInput("PO-1", "Supplier A", "2026-10-27", received = false),
            PurchaseDueInput("PO-2", "Supplier B", "2026-10-20", received = false),
            PurchaseDueInput("PO-3", "Supplier C", "2026-10-01", received = true),
            PurchaseDueInput("PO-4", "Supplier D", "2026-11-01", received = false),
        )
        val notes = AutomationEvaluators.purchasesDue(orders, today)
        assertEquals(listOf("due-PO-1", "due-PO-2"), notes.map { it.id })
    }

    @Test
    fun creditWarningFiresOnlyAboveLimit() {
        val exposures = listOf(
            CreditExposure("p1", "Ali Autos", money("101"), money("100")),
            CreditExposure("p2", "Bilal Traders", money("100"), money("100")),
            CreditExposure("p3", "Crown Motors", money("50"), money("100")),
        )
        val notes = AutomationEvaluators.creditWarnings(exposures, today)
        assertEquals(listOf("credit-p1"), notes.map { it.id })
        assertEquals(NotificationType.CREDIT_LIMIT_WARNING, notes.single().type)
    }

    @Test
    fun creditCurrencyMismatchThrows() {
        val exposures = listOf(
            CreditExposure("p1", "Ali Autos", money("101", "PKR"), money("100", "USD")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AutomationEvaluators.creditWarnings(exposures, today)
        }
    }

    @Test
    fun emptySnapshotsYieldEmptyResults() {
        assertTrue(AutomationEvaluators.lowStock(emptyList(), today).isEmpty())
        assertTrue(AutomationEvaluators.overdueInvoices(emptyList(), today).isEmpty())
        assertTrue(AutomationEvaluators.expiringStock(emptyList(), today).isEmpty())
        assertTrue(AutomationEvaluators.purchasesDue(emptyList(), today).isEmpty())
        assertTrue(AutomationEvaluators.creditWarnings(emptyList(), today).isEmpty())
        assertTrue(
            AutomationEvaluators.evaluateAll(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), today,
            ).isEmpty(),
        )
    }

    @Test
    fun evaluateAllCombinesEveryEvaluator() {
        val notes = AutomationEvaluators.evaluateAll(
            levels = listOf(StockLevel("i1", "Mobil 1", BigDecimal("1"), BigDecimal("5"))),
            invoices = listOf(OverdueInvoiceInput("INV-1", "Ali Autos", "2026-10-10", paid = false)),
            expiring = listOf(ExpiringStockInput("i2", "Oil B", "2026-10-20")),
            orders = listOf(PurchaseDueInput("PO-1", "Supplier A", "2026-10-20", received = false)),
            exposures = listOf(CreditExposure("p1", "Ali Autos", money("200"), money("100"))),
            today = today,
        )
        assertEquals(5, notes.size)
        assertEquals(
            listOf(
                NotificationType.LOW_STOCK,
                NotificationType.PAYMENT_OVERDUE,
                NotificationType.STOCK_EXPIRY,
                NotificationType.PURCHASE_DUE,
                NotificationType.CREDIT_LIMIT_WARNING,
            ),
            notes.map { it.type },
        )
    }
}
