package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16.3: price-list and entry codecs round-trip and tolerate legacy payloads. */
class PriceListsJsonTest {

    @Test fun priceListRoundTrip() {
        val list = PriceList(id = "pl-1", name = "Wholesale")
        assertEquals(list, PriceListJson.decode("pl-1", PriceListJson.encode(list)))
    }

    @Test fun priceListDecodeToleratesLegacyAndBadPayloads() {
        assertEquals("Legacy", PriceListJson.decode("pl-7", """{"uuid":"x","name":"Legacy"}""").name)
        assertEquals("", PriceListJson.decode("pl-8", "not json").name)
        assertEquals("", PriceListJson.decode("pl-9", "").name)
    }

    @Test fun entryRoundTrip() {
        val entry = PriceListItem(id = "e-1", priceListId = "pl-1", itemId = "i-1", rate = BigDecimal("950.50"))
        assertEquals(entry, PriceListItemJson.decode("e-1", PriceListItemJson.encode(entry)))
    }

    @Test fun entryRateAcceptedAsJsonNumber() {
        val decoded = PriceListItemJson.decode("e-2", """{"priceListId":"pl-1","itemId":"i-1","rate":42}""")
        assertEquals(BigDecimal("42"), decoded.rate)
    }

    @Test fun entryMissingOrBadRateDecodesAsNull() {
        assertEquals(null, PriceListItemJson.decode("e-3", """{"priceListId":"pl-1","itemId":"i-1"}""").rate)
        assertEquals(
            null,
            PriceListItemJson.decode("e-4", """{"priceListId":"pl-1","itemId":"i-1","rate":"soon"}""").rate,
        )
        val minimal = PriceListItemJson.decode("e-5", "junk")
        assertEquals("", minimal.priceListId)
        assertEquals(null, minimal.rate)
    }
}
