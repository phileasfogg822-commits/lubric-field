package com.lubrcapp.lubrcapp.domain.business

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalModelsTest {

    private fun money(amount: String, currency: String = "PKR") =
        Money.of(amount, CurrencyCode(currency))

    private fun invoice(id: String, amount: String, paid: String, currency: String = "PKR") =
        PortalInvoice(id, number = "INV-$id", amount = money(amount, currency), paid = money(paid, currency))

    @Test
    fun sectionsMatchSpecOrder() {
        assertEquals(
            listOf(
                PortalSection.ORDERS,
                PortalSection.INVOICES,
                PortalSection.STATEMENTS,
                PortalSection.PAYMENTS,
                PortalSection.CATALOG,
            ),
            PortalSection.entries.toList(),
        )
    }

    @Test
    fun dueIsAmountMinusPaid() {
        assertEquals(money("800"), PortalOps.due(invoice("1", "1000", "200")))
    }

    @Test
    fun fullyPaidInvoiceHasZeroDueAndCannotPay() {
        val paid = invoice("1", "1000", "1000")
        assertTrue(PortalOps.due(paid).isZero)
        assertFalse(PortalOps.canPay(paid))
    }

    @Test
    fun overpaidInvoiceHasNegativeDueAndCannotPay() {
        val overpaid = invoice("1", "1000", "1200")
        assertTrue(PortalOps.due(overpaid).isNegative)
        assertFalse(PortalOps.canPay(overpaid))
    }

    @Test
    fun unpaidInvoiceCanPay() {
        assertTrue(PortalOps.canPay(invoice("1", "1000", "0")))
    }

    @Test
    fun dueByCurrencyGroupsWithoutMixing() {
        val dues = PortalOps.dueByCurrency(
            listOf(
                invoice("1", "1000", "200"),
                invoice("2", "500", "100"),
                invoice("3", "100", "0", currency = "USD"),
            ),
        )
        assertEquals(money("1200"), dues[CurrencyCode("PKR")])
        assertEquals(money("100", "USD"), dues[CurrencyCode("USD")])
    }

    @Test
    fun dueByCurrencyOnEmptyListIsEmpty() {
        assertTrue(PortalOps.dueByCurrency(emptyList()).isEmpty())
    }

    @Test
    fun mismatchedPaidCurrencyThrows() {
        val mixed = PortalInvoice(
            "1",
            amount = money("1000", "PKR"),
            paid = money("200", "USD"),
        )
        assertThrows(IllegalArgumentException::class.java) { PortalOps.due(mixed) }
    }
}
