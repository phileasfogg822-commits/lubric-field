package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 14.3: the group form state maps to and from PartyGroup without losing data. */
class PartyGroupFormStateTest {

    @Test fun loadThenToGroupRoundTrips() {
        val group = PartyGroup(
            id = "g-1",
            name = "Dealer",
            priceLevel = "Dealer",
            discountPercent = BigDecimal("20"),
            paymentTerms = "Net 45",
            taxRule = "Exempt",
            salesperson = "Omar",
            territory = "South",
        )
        val form = PartyGroupFormState()
        form.load(group)
        assertEquals(group, form.toGroup("g-1"))
    }

    @Test fun blankOptionalsBecomeNullAndInvalidDiscountBlocks() {
        val form = PartyGroupFormState()
        form.name = " Cash "
        val group = form.toGroup("g-2")!!
        assertEquals("Cash", group.name)
        assertNull(group.priceLevel)
        assertNull(group.discountPercent)

        form.discountPercent = "ten"
        assertNull(form.toGroup("g-2"))
        form.discountPercent = " 10.5 "
        assertEquals(BigDecimal("10.5"), form.toGroup("g-2")!!.discountPercent)
    }
}
