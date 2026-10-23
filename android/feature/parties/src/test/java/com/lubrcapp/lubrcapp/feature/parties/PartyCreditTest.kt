package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 14.4: §18 credit rules over the opening balance — the 95%
 * near-limit threshold is the spec's own example, pinned exactly.
 */
class PartyCreditTest {

    private fun party(
        type: PartyType = PartyType.CUSTOMER,
        opening: String? = null,
        limit: String? = null,
        blocked: Boolean = false,
    ) = Party(
        id = "p",
        name = "P",
        type = type,
        openingBalance = opening?.let { BigDecimal(it) },
        creditLimit = limit?.let { BigDecimal(it) },
        isBlocked = blocked,
    )

    @Test fun withinNearAndOverLimitBoundaries() {
        assertEquals(PartyCredit.Status.WITHIN_LIMIT, PartyCredit.assess(party(opening = "50", limit = "100")).status)
        assertEquals(PartyCredit.Status.WITHIN_LIMIT, PartyCredit.assess(party(opening = "94.99", limit = "100")).status)
        // The spec §18 example: 95 of a 100 limit warns.
        assertEquals(PartyCredit.Status.NEAR_LIMIT, PartyCredit.assess(party(opening = "95", limit = "100")).status)
        assertEquals(PartyCredit.Status.NEAR_LIMIT, PartyCredit.assess(party(opening = "99.99", limit = "100")).status)
        assertEquals(PartyCredit.Status.OVER_LIMIT, PartyCredit.assess(party(opening = "100", limit = "100")).status)
        assertEquals(PartyCredit.Status.OVER_LIMIT, PartyCredit.assess(party(opening = "250", limit = "100")).status)
    }

    @Test fun blockedTakesPrecedenceOverEveryLimitState() {
        assertEquals(PartyCredit.Status.BLOCKED, PartyCredit.assess(party(opening = "500", limit = "100", blocked = true)).status)
        assertEquals(PartyCredit.Status.BLOCKED, PartyCredit.assess(party(blocked = true)).status)
    }

    @Test fun noUsableLimitMeansNoLimitState() {
        val noLimit = PartyCredit.assess(party(opening = "500"))
        assertEquals(PartyCredit.Status.NO_LIMIT, noLimit.status)
        assertNull(noLimit.utilization)
        assertEquals(BigDecimal("500"), noLimit.outstanding)
        // A zero limit is not a usable limit.
        assertEquals(PartyCredit.Status.NO_LIMIT, PartyCredit.assess(party(opening = "5", limit = "0")).status)
    }

    @Test fun onlyReceivableSideCountsAsOutstanding() {
        // Customer advance (negative opening) and supplier payable are not exposure.
        assertEquals(BigDecimal.ZERO, PartyCredit.assess(party(opening = "-80", limit = "100")).outstanding)
        assertEquals(PartyCredit.Status.WITHIN_LIMIT, PartyCredit.assess(party(opening = "-80", limit = "100")).status)
        assertEquals(BigDecimal.ZERO, PartyCredit.assess(party(type = PartyType.SUPPLIER, opening = "80", limit = "100")).outstanding)
        // A supplier advance (negative opening, receivable direction) does count.
        val supplierAdvance = PartyCredit.assess(party(type = PartyType.SUPPLIER, opening = "-96", limit = "100"))
        assertEquals(BigDecimal("96"), supplierAdvance.outstanding)
        assertEquals(PartyCredit.Status.NEAR_LIMIT, supplierAdvance.status)
        // No opening balance at all: zero outstanding, within any limit.
        val none = PartyCredit.assess(party(limit = "100"))
        assertEquals(BigDecimal.ZERO, none.outstanding)
        assertEquals(PartyCredit.Status.WITHIN_LIMIT, none.status)
    }
}
