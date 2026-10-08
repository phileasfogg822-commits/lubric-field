package com.lubrcapp.lubrcapp.domain.common

import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityTest {
    @Test
    fun fractionalQuantitiesRemainExact() {
        val result = Quantity.of("0.125") + Quantity.of("4.750")
        assertEquals(Quantity.of("4.875"), result)
    }

    @Test
    fun subtractionComparisonAndSignWork() {
        val result = Quantity.of("1.5") - Quantity.of("2")
        assertEquals(Quantity.of("-0.5"), result)
        assertTrue(result.isNegative)
        assertTrue(Quantity.of("2") > Quantity.of("1.999"))
    }

    @Test
    fun multiplicationAndExplicitRoundingWork() {
        val result = Quantity.of("1.25") * BigDecimal("3")
        assertEquals(Quantity.of("3.75"), result)
        assertEquals(
            Quantity.of("3.8"),
            result.rounded(1, RoundingMode.HALF_UP),
        )
    }
}
