package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 15.2: the item form state round-trips and validates like the party form. */
class ItemFormStateTest {

    @Test fun loadThenToItemRoundTripsEveryField() {
        val item = Item(
            id = "i-1",
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
        val form = ItemFormState()
        form.load(item)
        assertEquals(item, form.toItem("i-1"))
    }

    @Test fun blankOptionalsBecomeNullAndDefaultsApply() {
        val form = ItemFormState()
        form.name = "  Plain Item  "
        val item = form.toItem("i-2")!!
        assertEquals("Plain Item", item.name)
        assertEquals(ItemType.PRODUCT, item.type)
        assertNull(item.sku)
        assertNull(item.barcode)
        assertNull(item.categoryId)
        assertNull(item.brandId)
        assertNull(item.unit)
        assertNull(item.purchaseRate)
        assertNull(item.saleRate)
        assertNull(item.taxPercent)
        assertNull(item.openingStock)
        assertNull(item.description)
    }

    @Test fun categoryAndBrandIdsCarryThroughEdits() {
        val form = ItemFormState()
        form.load(Item(id = "i-3", name = "Linked", categoryId = "cat-9", brandId = "brand-9"))
        form.name = "Linked v2"
        val item = form.toItem("i-3")!!
        assertEquals("cat-9", item.categoryId)
        assertEquals("brand-9", item.brandId)
    }

    @Test fun invalidNumbersBlockTheSave() {
        val badRate = ItemFormState()
        badRate.name = "Bad"
        badRate.saleRate = "soon"
        assertNull(badRate.toItem("i-4"))

        val badStock = ItemFormState()
        badStock.name = "Bad"
        badStock.openingStock = "1.5.2"
        assertNull(badStock.toItem("i-4"))

        val padded = ItemFormState()
        padded.name = "Good"
        padded.saleRate = "  450.25  "
        assertEquals(BigDecimal("450.25"), padded.toItem("i-4")!!.saleRate)
    }

    @Test fun resetReturnsToBlankDefaults() {
        val form = ItemFormState()
        form.load(
            Item(
                id = "i-5",
                name = "Something",
                type = ItemType.SERVICE,
                categoryId = "cat-1",
                saleRate = BigDecimal("10"),
            ),
        )
        form.reset()
        assertEquals("", form.name)
        assertEquals(ItemType.PRODUCT, form.type)
        assertNull(form.categoryId)
        assertNull(form.brandId)
        assertEquals("", form.saleRate)
    }
}
