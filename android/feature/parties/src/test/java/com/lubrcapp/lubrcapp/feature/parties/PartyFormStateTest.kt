package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 14.2: the form state maps to and from the typed Party without losing data. */
class PartyFormStateTest {

    @Test fun loadThenToPartyRoundTripsEveryField() {
        val party = Party(
            id = "p-1",
            name = "  Full Party  ",
            type = PartyType.BOTH,
            businessName = "Full Traders",
            contactPerson = "Ali",
            mobile = "0300-1",
            whatsapp = "0300-2",
            phone = "042-3",
            email = "a@b.example",
            addresses = listOf(
                PartyAddress(PartyAddress.Kind.BILLING, line1 = "1 Main St", city = "Lahore", country = "PK"),
                PartyAddress(PartyAddress.Kind.SHIPPING, line1 = "9 Dock Rd", postalCode = "54000"),
            ),
            taxNumber = "TAX-9",
            nationalId = "35202-1",
            openingBalance = BigDecimal("42.50"),
            creditLimit = BigDecimal("5000"),
            creditDays = 30,
            paymentTerms = "Net 30",
            priceLevel = "Wholesale",
            groupId = "g-1",
            territory = "North",
            salesperson = "Sara",
            isBlocked = true,
            notes = "VIP",
        )
        val form = PartyFormState()
        form.load(party)
        // Name is trimmed on save; everything else must survive exactly.
        assertEquals(party.copy(name = "Full Party"), form.toParty("p-1"))
    }

    @Test fun blankOptionalsBecomeNullAndBlankAddressesAreDropped() {
        val form = PartyFormState()
        form.name = "Minimal"
        form.addresses = listOf(PartyAddressFormState(), PartyAddressFormState().apply { city = "Karachi" })
        val party = form.toParty("p-2")!!
        assertNull(party.businessName)
        assertNull(party.openingBalance)
        assertNull(party.creditLimit)
        assertNull(party.creditDays)
        assertNull(party.groupId)
        assertEquals(1, party.addresses.size)
        assertEquals("Karachi", party.addresses.single().city)
        assertEquals(PartyAddress.Kind.BILLING, party.addresses.single().kind)
    }

    @Test fun invalidNumbersBlockTheSave() {
        val badAmount = PartyFormState().apply { name = "X"; openingBalance = "abc" }
        assertNull(badAmount.toParty("p-3"))
        val badDays = PartyFormState().apply { name = "X"; creditDays = "1.5" }
        assertNull(badDays.toParty("p-3"))
        val good = PartyFormState().apply { name = "X"; openingBalance = " -12.5 "; creditDays = " 45 " }
        val party = good.toParty("p-3")!!
        assertEquals(BigDecimal("-12.5"), party.openingBalance)
        assertEquals(45, party.creditDays)
    }

    @Test fun resetClearsBackToDefaults() {
        val form = PartyFormState()
        form.load(Party(id = "p-4", name = "Loaded", type = PartyType.SUPPLIER, groupId = "g-9"))
        form.reset()
        assertEquals("", form.name)
        assertEquals(PartyType.CUSTOMER, form.type)
        assertNull(form.groupId)
        assertTrue(form.addresses.isEmpty())
    }
}
