package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 16.3: price-list form states trim and guard entry rates. */
class ItemPriceListsFormStatesTest {

    @Test fun priceListFormTrimsName() {
        val form = PriceListFormState()
        form.name = "  Wholesale  "
        assertEquals(PriceList("pl-1", "Wholesale"), form.toPriceList("pl-1"))
    }

    @Test fun entryFormBuildsValidEntry() {
        val form = PriceListItemFormState()
        form.itemId = "i-1"
        form.rate = "950.50"
        assertEquals(
            PriceListItem("e-1", "pl-1", "i-1", BigDecimal("950.50")),
            form.toEntry("e-1", "pl-1"),
        )
    }

    @Test fun entryFormAllowsZeroRate() {
        val form = PriceListItemFormState()
        form.itemId = "i-1"
        form.rate = "0"
        assertEquals(BigDecimal("0"), form.toEntry("e-1", "pl-1")?.rate)
    }

    @Test fun entryFormRejectsUnpickedItemAndBadRates() {
        val form = PriceListItemFormState()
        form.rate = "10"
        assertNull(form.toEntry("e-1", "pl-1"))
        form.itemId = "i-1"
        for (bad in listOf("", "soon", "-5")) {
            form.rate = bad
            assertNull("rate '$bad' must not save", form.toEntry("e-1", "pl-1"))
        }
    }

    @Test fun entryLoadDropsBlankItemAndNullRate() {
        val form = PriceListItemFormState()
        form.load(PriceListItem("e-9", "pl-1", "", null))
        assertNull(form.itemId)
        assertEquals("", form.rate)
    }
}
