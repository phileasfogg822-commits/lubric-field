package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.1: unit and conversion codecs round-trip and tolerate legacy payloads. */
class ItemUnitsJsonTest {

    @Test fun unitRoundTrip() {
        val unit = ItemUnit(id = "u-1", name = "Carton")
        assertEquals(unit, ItemUnitJson.decode("u-1", ItemUnitJson.encode(unit)))
    }

    @Test fun unitDecodeToleratesLegacyAndBadPayloads() {
        assertEquals("Legacy", ItemUnitJson.decode("u-7", """{"uuid":"x","name":"Legacy"}""").name)
        assertEquals("u-7", ItemUnitJson.decode("u-7", """{"name":"Legacy"}""").id)
        assertEquals("", ItemUnitJson.decode("u-8", "not json").name)
        assertEquals("", ItemUnitJson.decode("u-9", "").name)
    }

    @Test fun conversionRoundTrip() {
        val conversion = UnitConversion(id = "cv-1", fromUnitId = "u-carton", toUnitId = "u-bottle", factor = BigDecimal("12"))
        assertEquals(conversion, UnitConversionJson.decode("cv-1", UnitConversionJson.encode(conversion)))
    }

    @Test fun conversionFactorAcceptedAsJsonNumberAndKeepsPrecision() {
        val asNumber = UnitConversionJson.decode("cv-2", """{"fromUnitId":"a","toUnitId":"b","factor":0.5}""")
        assertEquals(BigDecimal("0.5"), asNumber.factor)
        val precise = UnitConversionJson.decode("cv-3", """{"fromUnitId":"a","toUnitId":"b","factor":"1000000.25"}""")
        assertEquals(BigDecimal("1000000.25"), precise.factor)
    }

    @Test fun conversionBadOrMissingFactorDecodesAsZeroSentinel() {
        assertEquals(
            BigDecimal.ZERO,
            UnitConversionJson.decode("cv-4", """{"fromUnitId":"a","toUnitId":"b","factor":"soon"}""").factor,
        )
        assertEquals(BigDecimal.ZERO, UnitConversionJson.decode("cv-5", "{}").factor)
        assertEquals(BigDecimal.ZERO, UnitConversionJson.decode("cv-6", "junk").factor)
    }

    @Test fun itemUnitIdRoundTripsThroughItemJson() {
        val item = Item(id = "i-1", name = "Oil", unit = "Carton", unitId = "u-1")
        val decoded = ItemJson.decode("i-1", ItemJson.encode(item))
        assertEquals("u-1", decoded.unitId)
        assertEquals("Carton", decoded.unit)
        // A pre-16.1 payload has no unitId and still decodes.
        val legacy = ItemJson.decode("i-2", """{"name":"Old Oil","unit":"Bottle"}""")
        assertEquals(null, legacy.unitId)
        assertEquals("Bottle", legacy.unit)
    }
}
