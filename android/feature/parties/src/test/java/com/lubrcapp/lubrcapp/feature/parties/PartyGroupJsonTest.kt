package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 14.3: the group codec round-trips and tolerates legacy/garbage payloads. */
class PartyGroupJsonTest {

    @Test fun fullRoundTrip() {
        val group = PartyGroup(
            id = "g-1",
            name = "Wholesale",
            priceLevel = "Wholesale",
            discountPercent = BigDecimal("12.50"),
            paymentTerms = "Net 30",
            taxRule = "GST 18%",
            salesperson = "Sara",
            territory = "North",
        )
        assertEquals(group, PartyGroupJson.decode("g-1", PartyGroupJson.encode(group)))
    }

    @Test fun minimalRoundTripAndRecordIdWins() {
        val group = PartyGroup(id = "g-2", name = "Retail")
        val decoded = PartyGroupJson.decode("g-2", PartyGroupJson.encode(group))
        assertEquals(group, decoded)
        assertNull(decoded.discountPercent)
    }

    @Test fun garbageDecodesToMinimalGroup() {
        assertEquals(PartyGroup(id = "g-3", name = ""), PartyGroupJson.decode("g-3", "not json"))
        assertEquals(PartyGroup(id = "g-3", name = ""), PartyGroupJson.decode("g-3", ""))
        assertEquals(PartyGroup(id = "g-3", name = ""), PartyGroupJson.decode("g-3", "[1,2]"))
    }

    @Test fun discountAcceptsNumberOrStringAndNullsDefault() {
        val asNumber = PartyGroupJson.decode("g-4", """{"name":"D","discountPercent":7.5}""")
        assertEquals(BigDecimal("7.5"), asNumber.discountPercent)
        val asString = PartyGroupJson.decode("g-4", """{"name":"D","discountPercent":"7.50"}""")
        assertEquals(BigDecimal("7.50"), asString.discountPercent)
        val explicitNull = PartyGroupJson.decode("g-4", """{"name":"D","discountPercent":null}""")
        assertNull(explicitNull.discountPercent)
        val unknownFields = PartyGroupJson.decode("g-4", """{"name":"D","futureField":true}""")
        assertEquals("D", unknownFields.name)
    }
}
