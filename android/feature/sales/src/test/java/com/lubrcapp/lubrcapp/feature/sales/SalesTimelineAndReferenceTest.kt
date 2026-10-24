package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesTimelineAndReferenceTest {

    @Test
    fun timelineEntryRoundTripsAndGarbageDecodesMinimal() {
        val entry = TimelineEntry(
            id = "t-1",
            documentId = "doc-1",
            documentType = "QUOTATION",
            documentNumber = "QT-2026-00001",
            partyId = "party-1",
            action = "SAVED",
            grandTotal = "1170.00",
            timestamp = "2026-10-23T18:00:00Z",
        )
        assertEquals(entry, TimelineEntryJson.decode("t-1", TimelineEntryJson.encode(entry)))
        val garbage = TimelineEntryJson.decode("t-2", "nope")
        assertEquals("t-2", garbage.id)
        assertEquals("", garbage.documentId)
    }

    @Test
    fun referenceDataReadsNamesAndAmountsTolerantly() {
        val parties = SalesReferenceData.partyOptions(
            listOf(
                OperationalRecord("p-1", """{"name":"Zafar Traders"}"""),
                OperationalRecord("p-2", """{}"""),
                OperationalRecord("p-3", "broken"),
            )
        )
        assertEquals(
            listOf(PartyOption("p-1", "Zafar Traders"), PartyOption("p-2", "p-2"), PartyOption("p-3", "p-3")),
            parties,
        )

        val items = SalesReferenceData.itemOptions(
            listOf(
                OperationalRecord("i-1", """{"name":"Engine Oil","saleRate":"4500.50","taxPercent":17}"""),
                OperationalRecord("i-2", """{"name":"Filter","saleRate":"x"}"""),
            )
        )
        assertEquals(BigDecimal("4500.50"), items[0].saleRate)
        assertEquals(BigDecimal("17"), items[0].taxPercent)
        assertNull(items[1].saleRate)
    }
}
