package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyJsonTest {

    private fun fullParty() = Party(
        id = "party-1",
        name = "Zafar Lubricants",
        type = PartyType.BOTH,
        businessName = "Zafar Lubricants (Pvt) Ltd",
        contactPerson = "Zafar Iqbal",
        mobile = "03001234567",
        whatsapp = "03001234567",
        phone = "04235710000",
        email = "zafar@example.com",
        addresses = listOf(
            PartyAddress(
                kind = PartyAddress.Kind.BILLING,
                line1 = "12 Main Boulevard",
                line2 = "Gulberg III",
                city = "Lahore",
                region = "Punjab",
                postalCode = "54660",
                country = "PK",
            ),
            PartyAddress(
                kind = PartyAddress.Kind.SHIPPING,
                line1 = "Plot 9, Industrial Estate",
                city = "Lahore",
                country = "PK",
            ),
        ),
        taxNumber = "NTN-1234567",
        nationalId = "35202-1234567-1",
        openingBalance = BigDecimal("1500.50"),
        creditLimit = BigDecimal("100000"),
        creditDays = 30,
        paymentTerms = "Net 30",
        priceLevel = "Wholesale",
        groupId = "group-1",
        territory = "Lahore North",
        salesperson = "Ahmed",
        isBlocked = true,
        notes = "Prefers morning deliveries",
    )

    @Test fun fullPartyRoundTrips() {
        val party = fullParty()
        assertEquals(party, PartyJson.decode(party.id, PartyJson.encode(party)))
    }

    @Test fun minimalPartyRoundTrips() {
        val party = Party(id = "p-2", name = "Cash Customer")
        assertEquals(party, PartyJson.decode(party.id, PartyJson.encode(party)))
    }

    @Test fun legacyNameOnlyPayloadDecodes() {
        val payload = """{"uuid":"legacy-uuid","name":"Zafar Lubricants"}"""
        val party = PartyJson.decode("record-9", payload)
        // The store's record id wins over the payload's own uuid.
        assertEquals("record-9", party.id)
        assertEquals("Zafar Lubricants", party.name)
        assertEquals(PartyType.CUSTOMER, party.type)
        assertTrue(party.addresses.isEmpty())
        assertNull(party.notes)
        assertNull(party.openingBalance)
    }

    @Test fun legacyDescriptionMapsToNotes() {
        val payload = """{"name":"ABC Pharmacy","description":"Wholesale account"}"""
        val party = PartyJson.decode("record-2", payload)
        assertEquals("Wholesale account", party.notes)
    }

    @Test fun blankGarbageAndNonObjectPayloadsDecodeToMinimal() {
        for (payload in listOf("", "   ", "{}", "not json", "[1,2]", "42")) {
            val party = PartyJson.decode("record-x", payload)
            assertEquals("record-x", party.id)
            assertEquals("", party.name)
            assertEquals(PartyType.CUSTOMER, party.type)
        }
    }

    @Test fun stringAndNumberTypedAmountsBothDecode() {
        val payload = """{"name":"A","openingBalance":"2500.75","creditLimit":10000,"creditDays":"30"}"""
        val party = PartyJson.decode("record-3", payload)
        assertEquals(BigDecimal("2500.75"), party.openingBalance)
        assertEquals(BigDecimal("10000"), party.creditLimit)
        assertEquals(30, party.creditDays)
    }

    @Test fun unknownFieldsAreIgnored() {
        val payload = """{"name":"A","futureField":{"nested":true},"another":[1,2,3]}"""
        val party = PartyJson.decode("record-4", payload)
        assertEquals("A", party.name)
    }

    @Test fun legacyFlatAddressFoldsIntoBillingAddress() {
        val payload = """{"name":"A","address":"1 Canal Road","city":"Lahore","country":"PK"}"""
        val party = PartyJson.decode("record-5", payload)
        assertEquals(1, party.addresses.size)
        val address = party.addresses.single()
        assertEquals(PartyAddress.Kind.BILLING, address.kind)
        assertEquals("1 Canal Road", address.line1)
        assertEquals("Lahore", address.city)
        assertEquals("PK", address.country)
    }

    @Test fun explicitJsonNullsDefault() {
        val payload = """{"name":null,"mobile":null,"openingBalance":null,"isBlocked":null}"""
        val party = PartyJson.decode("record-6", payload)
        assertEquals("", party.name)
        assertNull(party.mobile)
        assertNull(party.openingBalance)
        assertEquals(false, party.isBlocked)
    }

    @Test fun typeParsingIsCaseTolerantAndDefaults() {
        assertEquals(PartyType.SUPPLIER, PartyJson.decode("r", """{"type":"supplier"}""").type)
        assertEquals(PartyType.BOTH, PartyJson.decode("r", """{"type":"Both"}""").type)
        assertEquals(PartyType.CUSTOMER, PartyJson.decode("r", """{"type":"weird"}""").type)
        assertEquals(PartyType.CUSTOMER, PartyJson.decode("r", """{"name":"x"}""").type)
    }
}
