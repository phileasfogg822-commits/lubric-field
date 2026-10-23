package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 15.1: ItemJson round-trips the full §20/§26 field set and tolerates legacy payloads. */
class ItemJsonTest {

    @Test fun fullItemRoundTripsExactly() {
        val item = Item(
            id = "item-1",
            name = "Engine Oil 20W-50",
            type = ItemType.PRODUCT,
            sku = "EO-2050",
            barcode = "8964001234567",
            categoryId = "cat-1",
            brandId = "brand-1",
            unit = "Bottle",
            purchaseRate = BigDecimal("850.50"),
            saleRate = BigDecimal("1100"),
            wholesaleRate = BigDecimal("1020.75"),
            dealerRate = BigDecimal("990"),
            minimumRate = BigDecimal("950"),
            mrp = BigDecimal("1250"),
            taxPercent = BigDecimal("18"),
            openingStock = BigDecimal("24"),
            description = "Semi-synthetic, 4L",
        )
        val decoded = ItemJson.decode("item-1", ItemJson.encode(item))
        assertEquals(item, decoded)
    }

    @Test fun legacyNameOnlyPayloadDecodesMinimal() {
        val decoded = ItemJson.decode("item-2", """{"uuid":"legacy-id","name":"Old Oil"}""")
        assertEquals("item-2", decoded.id)
        assertEquals("Old Oil", decoded.name)
        assertEquals(ItemType.PRODUCT, decoded.type)
        assertNull(decoded.sku)
        assertNull(decoded.saleRate)
        assertNull(decoded.openingStock)
    }

    @Test fun recordIdWinsOverPayloadUuid() {
        val decoded = ItemJson.decode("store-id", """{"uuid":"payload-id","name":"X"}""")
        assertEquals("store-id", decoded.id)
    }

    @Test fun numbersAsStringsAndJsonNumbersBothDecode() {
        val fromStrings = ItemJson.decode(
            "item-3",
            """{"name":"Filter","saleRate":"450.25","openingStock":"10"}""",
        )
        assertEquals(BigDecimal("450.25"), fromStrings.saleRate)
        assertEquals(BigDecimal("10"), fromStrings.openingStock)

        val fromNumbers = ItemJson.decode(
            "item-3",
            """{"name":"Filter","saleRate":450.25,"taxPercent":18}""",
        )
        assertEquals(BigDecimal("450.25"), fromNumbers.saleRate)
        assertEquals(BigDecimal("18"), fromNumbers.taxPercent)
    }

    @Test fun unparseablePayloadDecodesToMinimalItem() {
        val decoded = ItemJson.decode("item-4", "not json at all")
        assertEquals("item-4", decoded.id)
        assertEquals("", decoded.name)
        assertEquals(ItemType.PRODUCT, decoded.type)
    }

    @Test fun blankPayloadDecodesToMinimalItem() {
        val decoded = ItemJson.decode("item-5", "")
        assertEquals("item-5", decoded.id)
        assertEquals("", decoded.name)
    }

    @Test fun everyTypeDecodesAndUnknownTypeFallsBackToProduct() {
        ItemType.entries.forEach { type ->
            val decoded = ItemJson.decode("item-6", """{"name":"T","type":"${type.name}"}""")
            assertEquals(type, decoded.type)
        }
        val unknown = ItemJson.decode("item-6", """{"name":"T","type":"WIDGET"}""")
        assertEquals(ItemType.PRODUCT, unknown.type)
        val lower = ItemJson.decode("item-6", """{"name":"T","type":"service"}""")
        assertEquals(ItemType.SERVICE, lower.type)
    }

    @Test fun decimalPrecisionSurvivesRoundTrip() {
        val item = Item(
            id = "item-7",
            name = "Precise",
            saleRate = BigDecimal("0.10"),
            purchaseRate = BigDecimal("999999999.99"),
        )
        val decoded = ItemJson.decode("item-7", ItemJson.encode(item))
        assertEquals(BigDecimal("0.10"), decoded.saleRate)
        assertEquals("0.10", decoded.saleRate!!.toPlainString())
        assertEquals(BigDecimal("999999999.99"), decoded.purchaseRate)
    }

    @Test fun explicitNullsAndBadNumbersAreTolerated() {
        val decoded = ItemJson.decode(
            "item-8",
            """{"name":"N","saleRate":null,"mrp":"abc","sku":null}""",
        )
        assertNull(decoded.saleRate)
        assertNull(decoded.mrp)
        assertNull(decoded.sku)
    }
}
