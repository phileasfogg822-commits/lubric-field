package com.lubrcapp.lubrcapp.core.designsystem

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CashFlowFractionsTest {

    @Test
    fun largerSideIsFullBar() {
        val (inFraction, outFraction) = cashFlowFractions(
            BigDecimal("5000"),
            BigDecimal("1500"),
        )
        assertEquals(1f, inFraction, 0.001f)
        assertEquals(0.3f, outFraction, 0.001f)
    }

    @Test
    fun equalSidesAreBothFull() {
        val (inFraction, outFraction) = cashFlowFractions(
            BigDecimal("2500"),
            BigDecimal("2500"),
        )
        assertEquals(1f, inFraction, 0.001f)
        assertEquals(1f, outFraction, 0.001f)
    }

    @Test
    fun bothZeroYieldsEmptyBars() {
        val (inFraction, outFraction) = cashFlowFractions(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        )
        assertEquals(0f, inFraction, 0.001f)
        assertEquals(0f, outFraction, 0.001f)
    }

    @Test
    fun oneZeroSideYieldsEmptyAndFull() {
        val (inFraction, outFraction) = cashFlowFractions(
            BigDecimal.ZERO,
            BigDecimal("2000"),
        )
        assertEquals(0f, inFraction, 0.001f)
        assertEquals(1f, outFraction, 0.001f)
    }

    @Test
    fun negativeInputsClampToZero() {
        val (inFraction, outFraction) = cashFlowFractions(
            BigDecimal("-500"),
            BigDecimal("1000"),
        )
        assertEquals(0f, inFraction, 0.001f)
        assertEquals(1f, outFraction, 0.001f)
    }
}
