package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartLine
import com.lubrcapp.lubrcapp.domain.sales.PosLineDiscount
import com.lubrcapp.lubrcapp.domain.sales.PosParty
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosCounterMapperTest {

    private val pkr = CurrencyCode("PKR")

    private fun cart() = PosCart(
        id = "cart-1",
        lines = listOf(
            PosCartLine(
                id = "line-1",
                description = "Engine Oil",
                quantity = Quantity.of("2"),
                rate = Money.of("500", pkr),
                discount = PosLineDiscount.Percent(Percentage.of("10")),
            ),
            PosCartLine(
                id = "line-2",
                description = "Filter",
                quantity = Quantity.of("1"),
                rate = Money.of("300", pkr),
            ),
        ),
    )

    @Test fun cartLinesMapToNetAmounts() {
        val document = cartToPrintable(cart(), partyName = null, currency = pkr)
        assertEquals("POS", document.title)
        assertEquals("", document.documentNumber)
        assertEquals("", document.date)
        assertNull(document.partyName)
        // Line 1: 2 x 500 = 1000, less 10% = 900 net. No tax, no
        // further discount: the builder must not subtract again.
        assertEquals(2, document.lines.size)
        assertEquals(BigDecimal("900"), document.lines[0].amount)
        assertEquals(BigDecimal("300"), document.lines[1].amount)
        assertEquals(BigDecimal("1200"), document.subtotal)
        assertEquals(BigDecimal("1200"), document.grandTotal)
    }

    @Test fun emptyCartMapsToZeroTotals() {
        val document = cartToPrintable(PosCart(id = "cart-empty"), partyName = null, currency = pkr)
        assertEquals(0, document.lines.size)
        assertEquals(BigDecimal.ZERO, document.subtotal)
        assertEquals(BigDecimal.ZERO, document.grandTotal)
    }

    @Test fun walkInMapsToNullPartyAndNamedToId() {
        assertNull(cartPartyName(PosCart(id = "cart-walkin")))
        assertEquals(
            "party-9",
            cartPartyName(
                PosCart(
                    id = "cart-named",
                    party = PosParty.Named(com.lubrcapp.lubrcapp.domain.common.PartyId("party-9")),
                ),
            ),
        )
    }
}
