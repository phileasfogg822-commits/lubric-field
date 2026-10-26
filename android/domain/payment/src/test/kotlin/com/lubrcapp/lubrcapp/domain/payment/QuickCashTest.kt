package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuickCashTest {

    private val pkr = CurrencyCode("PKR")
    private val usd = CurrencyCode("USD")

    @Test
    fun exactTenderPaysZeroChange() {
        val settlement = QuickCash.settle(Money.of("750", pkr), Money.of("750", pkr))
        assertEquals(CashSettlement.Paid(Money.zero(pkr)), settlement)
    }

    @Test
    fun overTenderReturnsChange() {
        val settlement = QuickCash.settle(Money.of("750", pkr), Money.of("1000", pkr))
        assertEquals(CashSettlement.Paid(Money.of("250", pkr)), settlement)
    }

    @Test
    fun underTenderReportsShortfall() {
        val settlement = QuickCash.settle(Money.of("750", pkr), Money.of("500", pkr))
        assertEquals(CashSettlement.Shortfall(Money.of("250", pkr)), settlement)
    }

    @Test
    fun currencyMismatchThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            QuickCash.settle(Money.of("750", pkr), Money.of("1000", usd))
        }
    }

    @Test
    fun buttonAmountsExactFirstThenDenominations() {
        val buttons = QuickCash.buttonAmounts(Money.of("750", pkr))
        assertEquals(
            listOf(
                Money.of("750", pkr),
                Money.of("500", pkr),
                Money.of("1000", pkr),
                Money.of("5000", pkr),
            ),
            buttons,
        )
    }

    @Test
    fun buttonAmountsUseTotalCurrency() {
        val buttons = QuickCash.buttonAmounts(Money.of("10", usd))
        assertEquals(usd, buttons[1].currency)
        assertEquals(Money.of("500", usd), buttons[1])
    }

    @Test
    fun customDenominationsHonored() {
        val buttons = QuickCash.buttonAmounts(
            Money.of("100", pkr),
            listOf(BigDecimal("200")),
        )
        assertEquals(
            listOf(Money.of("100", pkr), Money.of("200", pkr)),
            buttons,
        )
    }
}
