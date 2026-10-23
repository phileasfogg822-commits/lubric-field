package com.lubrcapp.lubrcapp.feature.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 15.3: category/brand form states round-trip; a category cannot parent itself. */
class ItemCatalogFormStatesTest {

    @Test fun categoryLoadAndSaveRoundTrips() {
        val category = ItemCategory(id = "c-1", name = "Engine Oils", parentId = "c-0")
        val form = ItemCategoryFormState()
        form.load(category)
        assertEquals(category, form.toCategory("c-1"))
    }

    @Test fun categoryNameIsTrimmedAndParentKept() {
        val form = ItemCategoryFormState()
        form.name = "  Oils  "
        form.parentId = "c-0"
        val category = form.toCategory("c-9")
        assertEquals("Oils", category.name)
        assertEquals("c-0", category.parentId)
    }

    @Test fun categorySelfParentIsDropped() {
        val form = ItemCategoryFormState()
        form.name = "Loopy"
        form.parentId = "c-5"
        assertNull(form.toCategory("c-5").parentId)
    }

    @Test fun categoryResetClearsParent() {
        val form = ItemCategoryFormState()
        form.load(ItemCategory(id = "c-1", name = "X", parentId = "c-0"))
        form.reset()
        assertEquals("", form.name)
        assertNull(form.parentId)
    }

    @Test fun brandLoadAndSaveRoundTrips() {
        val brand = Brand(id = "b-1", name = "Shell")
        val form = BrandFormState()
        form.load(brand)
        assertEquals(brand, form.toBrand("b-1"))
        form.reset()
        assertEquals("", form.name)
    }
}
