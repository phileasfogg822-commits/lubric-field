package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 14.2: opening-balance direction follows type and sign — never invented. */
class PartyDisplayTest {

    private fun party(type: PartyType, balance: String?) = Party(
        id = "p",
        name = "P",
        type = type,
        openingBalance = balance?.let { BigDecimal(it) },
    )

    @Test fun customerDirections() {
        assertEquals(OpeningBalanceDirection.RECEIVABLE, party(PartyType.CUSTOMER, "100").openingBalanceDirection())
        assertEquals(OpeningBalanceDirection.PAYABLE, party(PartyType.CUSTOMER, "-100").openingBalanceDirection())
        assertEquals(OpeningBalanceDirection.RECEIVABLE, party(PartyType.BOTH, "0.01").openingBalanceDirection())
    }

    @Test fun supplierDirectionsAreMirrored() {
        assertEquals(OpeningBalanceDirection.PAYABLE, party(PartyType.SUPPLIER, "250").openingBalanceDirection())
        assertEquals(OpeningBalanceDirection.RECEIVABLE, party(PartyType.SUPPLIER, "-250").openingBalanceDirection())
    }

    @Test fun otherIsNeutralAndEmptyBalancesShowNoLine() {
        assertEquals(OpeningBalanceDirection.NEUTRAL, party(PartyType.OTHER, "75").openingBalanceDirection())
        assertNull(party(PartyType.CUSTOMER, "0").openingBalanceDirection())
        assertNull(party(PartyType.CUSTOMER, "0.00").openingBalanceDirection())
        assertNull(party(PartyType.SUPPLIER, null).openingBalanceDirection())
    }
}
