package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    private val usd = CurrencyCode.of("usd")
    private val pkr = CurrencyCode.of("PKR")

    @Test
    fun additionIsDecimalExact() {
        val result = Money.of("0.1", usd) + Money.of("0.2", usd)
        assertEquals(Money.of("0.3", usd), result)
    }

    @Test
    fun subtractionAndMultiplicationRemainExact() {
        val result = (Money.of("10.25", usd) - Money.of("0.25", usd)) * BigDecimal("1.5")
        assertEquals(Money.of("15.0", usd), result)
    }

    @Test
    fun comparisonsAreScaleInsensitive() {
        assertEquals(Money.of("1.0", usd), Money.of("1.00", usd))
        assertTrue(Money.of("2", usd) > Money.of("1.999", usd))
    }

    @Test
    fun zeroAndNegativeValuesAreSupported() {
        assertTrue(Money.zero(usd).isZero)
        assertTrue(Money.of("-1.25", usd).isNegative)
    }

    @Test
    fun mismatchedCurrenciesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1", usd) + Money.of("1", pkr)
        }
    }

    @Test
    fun roundingIsExplicit() {
        assertEquals(
            Money.of("10.13", usd),
            Money.of("10.125", usd).rounded(2, RoundingMode.HALF_UP),
        )
    }
}
