package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 16.1: unit/conversion form states guard meaningless conversions. */
class ItemUnitsFormStatesTest {

    @Test fun unitFormTrimsName() {
        val form = ItemUnitFormState()
        form.name = "  Carton  "
        assertEquals("Carton", form.toUnit("u-1").name)
        assertEquals("u-1", form.toUnit("u-1").id)
    }

    @Test fun conversionFormBuildsValidConversion() {
        val form = UnitConversionFormState()
        form.fromUnitId = "carton"
        form.toUnitId = "bottle"
        form.factor = "12"
        val conversion = form.toConversion("cv-1")
        assertEquals(UnitConversion("cv-1", "carton", "bottle", BigDecimal("12")), conversion)
    }

    @Test fun conversionFormRejectsSameUnitBothSides() {
        val form = UnitConversionFormState()
        form.fromUnitId = "carton"
        form.toUnitId = "carton"
        form.factor = "12"
        assertNull(form.toConversion("cv-1"))
    }

    @Test fun conversionFormRejectsUnpickedSides() {
        val form = UnitConversionFormState()
        form.factor = "12"
        assertNull(form.toConversion("cv-1"))
        form.fromUnitId = "carton"
        assertNull(form.toConversion("cv-1"))
    }

    @Test fun conversionFormRejectsBadFactors() {
        val form = UnitConversionFormState()
        form.fromUnitId = "carton"
        form.toUnitId = "bottle"
        for (bad in listOf("", "soon", "0", "-3", "1.2.3")) {
            form.factor = bad
            assertNull("factor '$bad' must not save", form.toConversion("cv-1"))
        }
    }

    @Test fun conversionLoadDropsZeroSentinelAndBlankIds() {
        val form = UnitConversionFormState()
        form.load(UnitConversion("cv-9", "", "bottle", BigDecimal.ZERO))
        assertNull(form.fromUnitId)
        assertEquals("bottle", form.toUnitId)
        assertEquals("", form.factor)
        form.load(UnitConversion("cv-8", "carton", "bottle", BigDecimal("12")))
        assertEquals("carton", form.fromUnitId)
        assertEquals("12", form.factor)
    }
}
