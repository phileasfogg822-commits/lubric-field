package com.lubrcapp.lubrcapp.feature.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 16.5: barcode lookup is exact; name search is a case-insensitive substring. */
class ItemSearchTest {

    private val oil = Item(id = "i-1", name = "Engine Oil 5W-30", barcode = "8901234567890")
    private val filter = Item(id = "i-2", name = "Oil Filter")
    private val items = listOf(oil, filter)

    @Test fun exactBarcodeFindsItem() {
        assertEquals(oil, ItemSearch.findByBarcode(items, "8901234567890"))
        assertEquals(oil, ItemSearch.findByBarcode(items, "  8901234567890  "))
    }

    @Test fun partialOrUnknownBarcodeFindsNothing() {
        assertNull(ItemSearch.findByBarcode(items, "890123"))
        assertNull(ItemSearch.findByBarcode(items, "0000000000000"))
        assertNull(ItemSearch.findByBarcode(items, ""))
    }

    @Test fun filterReturnsExactBarcodeHitAlone() {
        assertEquals(listOf(oil), ItemSearch.filter(items, "8901234567890"))
    }

    @Test fun filterFallsBackToNameSubstring() {
        assertEquals(items, ItemSearch.filter(items, "oil"))
        assertEquals(listOf(filter), ItemSearch.filter(items, "FILTER"))
        assertEquals(emptyList<Item>(), ItemSearch.filter(items, "brake"))
    }

    @Test fun blankQueryReturnsEverything() {
        assertEquals(items, ItemSearch.filter(items, ""))
        assertEquals(items, ItemSearch.filter(items, "   "))
    }
}
