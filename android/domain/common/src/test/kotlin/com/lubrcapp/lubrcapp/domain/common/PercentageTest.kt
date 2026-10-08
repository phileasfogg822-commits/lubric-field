package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PercentageTest {
    @Test
    fun convertsPercentPointsToFractionExactly() {
        assertEquals(0, Percentage.of("17.5").asFraction().compareTo(BigDecimal("0.175")))
    }

    @Test
    fun appliesToMoneyWithoutFloatingPoint() {
        val currency = CurrencyCode.of("USD")
        assertEquals(
            Money.of("15", currency),
            Percentage.of("15").applyTo(Money.of("100", currency)),
        )
    }

    @Test
    fun negativePercentagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Percentage.of("-0.01")
        }
    }
}
