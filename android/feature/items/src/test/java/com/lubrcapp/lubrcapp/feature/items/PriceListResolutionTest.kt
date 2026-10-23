package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 16.3: list rate wins, sale rate is the fallback, nothing is invented. */
class PriceListResolutionTest {

    private val item = Item(id = "i-1", name = "Oil", saleRate = BigDecimal("1100"))
    private val entry = PriceListItem(id = "e-1", priceListId = "pl-1", itemId = "i-1", rate = BigDecimal("950"))

    @Test fun listRateWinsOverSaleRate() {
        assertEquals(BigDecimal("950"), PriceListResolution.resolve(item, "pl-1", listOf(entry)))
    }

    @Test fun fallsBackToSaleRateWithoutEntry() {
        assertEquals(BigDecimal("1100"), PriceListResolution.resolve(item, "pl-1", emptyList()))
        assertEquals(BigDecimal("1100"), PriceListResolution.resolve(item, "pl-2", listOf(entry)))
    }

    @Test fun nullWhenNeitherEntryNorSaleRate() {
        val bare = Item(id = "i-2", name = "Service")
        assertNull(PriceListResolution.resolve(bare, "pl-1", emptyList()))
    }

    @Test fun entryWithUnreadableRateIsIgnored() {
        val damaged = PriceListItem(id = "e-2", priceListId = "pl-1", itemId = "i-1", rate = null)
        assertEquals(BigDecimal("1100"), PriceListResolution.resolve(item, "pl-1", listOf(damaged)))
    }

    @Test fun otherItemsEntriesNeverLeak() {
        val foreign = PriceListItem(id = "e-3", priceListId = "pl-1", itemId = "i-9", rate = BigDecimal("1"))
        assertEquals(BigDecimal("1100"), PriceListResolution.resolve(item, "pl-1", listOf(foreign)))
    }
}
