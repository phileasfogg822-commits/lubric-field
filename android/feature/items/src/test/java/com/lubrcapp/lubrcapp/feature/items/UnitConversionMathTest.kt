package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 16.1: the conversion rule converts one hop and never guesses a path. */
class UnitConversionMathTest {

    private val cartonToBottle = UnitConversion("cv-1", "carton", "bottle", BigDecimal("12"))

    @Test fun sameUnitReturnsAmountUnchanged() {
        assertEquals(
            BigDecimal("5"),
            UnitConversionMath.convert(BigDecimal("5"), "carton", "carton", emptyList()),
        )
    }

    @Test fun directRecordMultiplies() {
        assertEquals(
            BigDecimal("24"),
            UnitConversionMath.convert(BigDecimal("2"), "carton", "bottle", listOf(cartonToBottle)),
        )
    }

    @Test fun inverseRecordDividesExactly() {
        assertEquals(
            BigDecimal("2"),
            UnitConversionMath.convert(BigDecimal("24"), "bottle", "carton", listOf(cartonToBottle)),
        )
    }

    @Test fun inverseDivisionKeepsTenDecimalPrecision() {
        val result = UnitConversionMath.convert(BigDecimal("1"), "bottle", "carton", listOf(cartonToBottle))
        assertTrue(result != null && result.compareTo(BigDecimal("0.0833333333")) == 0)
    }

    @Test fun missingPathReturnsNull() {
        assertNull(UnitConversionMath.convert(BigDecimal("2"), "carton", "liter", listOf(cartonToBottle)))
        assertNull(UnitConversionMath.convert(BigDecimal("2"), "carton", "bottle", emptyList()))
    }

    @Test fun nonPositiveFactorIsNoPathEitherDirection() {
        val zero = UnitConversion("cv-0", "carton", "bottle", BigDecimal.ZERO)
        assertNull(UnitConversionMath.convert(BigDecimal("2"), "carton", "bottle", listOf(zero)))
        assertNull(UnitConversionMath.convert(BigDecimal("2"), "bottle", "carton", listOf(zero)))
    }

    @Test fun noChainingThroughAThirdUnit() {
        val bottleToLiter = UnitConversion("cv-2", "bottle", "liter", BigDecimal("0.5"))
        assertNull(
            UnitConversionMath.convert(BigDecimal("2"), "carton", "liter", listOf(cartonToBottle, bottleToLiter)),
        )
    }
}
