package com.lubrcapp.lubrcapp.feature.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 15.3: category and brand codecs round-trip and tolerate legacy payloads. */
class ItemCatalogJsonTest {

    @Test fun categoryRoundTripsWithParent() {
        val category = ItemCategory(id = "c-1", name = "Engine Oils", parentId = "c-0")
        assertEquals(category, ItemCategoryJson.decode("c-1", ItemCategoryJson.encode(category)))
    }

    @Test fun topLevelCategoryRoundTrips() {
        val category = ItemCategory(id = "c-2", name = "Oils")
        val decoded = ItemCategoryJson.decode("c-2", ItemCategoryJson.encode(category))
        assertEquals(category, decoded)
        assertNull(decoded.parentId)
    }

    @Test fun categoryLegacyAndBadPayloadsDecodeMinimal() {
        val legacy = ItemCategoryJson.decode("c-3", """{"uuid":"u1","name":"Old Cat"}""")
        assertEquals("c-3", legacy.id)
        assertEquals("Old Cat", legacy.name)
        assertNull(legacy.parentId)

        val bad = ItemCategoryJson.decode("c-4", "not json")
        assertEquals("c-4", bad.id)
        assertEquals("", bad.name)
    }

    @Test fun brandRoundTrips() {
        val brand = Brand(id = "b-1", name = "Shell")
        assertEquals(brand, BrandJson.decode("b-1", BrandJson.encode(brand)))
    }

    @Test fun brandLegacyAndBadPayloadsDecodeMinimal() {
        val legacy = BrandJson.decode("b-2", """{"uuid":"u9","name":"Castrol"}""")
        assertEquals("b-2", legacy.id)
        assertEquals("Castrol", legacy.name)

        val bad = BrandJson.decode("b-3", "")
        assertEquals("b-3", bad.id)
        assertEquals("", bad.name)
    }
}
