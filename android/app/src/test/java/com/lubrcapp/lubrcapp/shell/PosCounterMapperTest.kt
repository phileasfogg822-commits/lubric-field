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
import org.junit.Assert.assertTrue
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
        // Money-derived amounts are scale-normalized, so compare
        // with compareTo, never BigDecimal.equals.
        assertEquals(2, document.lines.size)
        assertTrue(document.lines[0].amount.compareTo(BigDecimal("900")) == 0)
        assertTrue(document.lines[1].amount.compareTo(BigDecimal("300")) == 0)
        assertTrue(document.subtotal.compareTo(BigDecimal("1200")) == 0)
        assertTrue(document.grandTotal.compareTo(BigDecimal("1200")) == 0)
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
