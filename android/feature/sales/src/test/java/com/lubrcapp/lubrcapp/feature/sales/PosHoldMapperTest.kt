package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartLine
import com.lubrcapp.lubrcapp.domain.sales.PosLineDiscount
import com.lubrcapp.lubrcapp.domain.sales.PosParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PosHoldMapperTest {

    private val pkr = CurrencyCode("PKR")

    private fun cart(party: PosParty = PosParty.WalkIn) = PosCart(
        id = "cart-1",
        party = party,
        lines = listOf(
            PosCartLine(
                id = "l1",
                itemId = ItemId("item-1"),
                description = "Engine Oil",
                quantity = Quantity.of("2"),
                rate = Money.of("100", pkr),
                discount = PosLineDiscount.Percent(Percentage.of("10")),
            ),
            PosCartLine(
                id = "l2",
                itemId = ItemId("item-2"),
                description = "Filter",
                quantity = Quantity.of("1"),
                rate = Money.of("50", pkr),
                discount = PosLineDiscount.Fixed(Money.of("5", pkr)),
            ),
        ),
    )

    @Test
    fun holdMapsCartToDraftInvoice() {
        val document = PosHoldMapper.cartToDocument(cart())
        assertEquals("cart-1", document.id)
        assertEquals(SalesDocumentType.INVOICE, document.type)
        assertEquals("DRAFT", document.status)
        assertEquals("", document.number)
        assertEquals("", document.date)
        assertEquals(null, document.partyId)
    }

    @Test
    fun holdMapsWalkInToNullPartyAndNamedToId() {
        val named = PosHoldMapper.cartToDocument(cart(PosParty.Named(PartyId("party-1"))))
        assertEquals("party-1", named.partyId)
    }

    @Test
    fun holdMapsLinesWithPositionsAndDiscounts() {
        val lines = PosHoldMapper.cartToLines(cart())
        assertEquals(2, lines.size)
        assertEquals("cart-1", lines[0].orderId)
        assertEquals(0, lines[0].position)
        assertEquals(1, lines[1].position)
        assertEquals("PERCENT", lines[0].discountKind)
        assertEquals(0, java.math.BigDecimal("10").compareTo(lines[0].discountValue))
        assertEquals("FIXED", lines[1].discountKind)
        assertEquals(0, java.math.BigDecimal("5").compareTo(lines[1].discountValue))
    }

    @Test
    fun resumeRoundTripsCart() {
        val original = cart(PosParty.Named(PartyId("party-1")))
        val resumed = PosHoldMapper.documentToCart(
            PosHoldMapper.cartToDocument(original),
            PosHoldMapper.cartToLines(original),
            pkr,
        )
        assertEquals(original, resumed)
    }

    @Test
    fun resumeWalkInWhenPartyMissingOrBlank() {
        val document = PosHoldMapper.cartToDocument(cart())
        val resumed = PosHoldMapper.documentToCart(document, emptyList(), pkr)
        assertEquals(PosParty.WalkIn, resumed.party)
        assertEquals("cart-1", resumed.id)
        assertEquals(0, resumed.lines.size)
    }

    @Test
    fun resumeSortsLinesByPosition() {
        val lines = PosHoldMapper.cartToLines(cart()).reversed()
        val resumed = PosHoldMapper.documentToCart(
            PosHoldMapper.cartToDocument(cart()),
            lines,
            pkr,
        )
        assertEquals(listOf("l1", "l2"), resumed.lines.map { it.id })
    }

    @Test
    fun resumeDropsTaxComponents() {
        val taxed = PosHoldMapper.cartToLines(cart()).map {
            it.copy(taxComponents = listOf(SalesTaxComponent("GST", java.math.BigDecimal("18"))))
        }
        val resumed = PosHoldMapper.documentToCart(
            PosHoldMapper.cartToDocument(cart()),
            taxed,
            pkr,
        )
        assertEquals(cart().lines, resumed.lines)
    }

    @Test
    fun resumeRejectsNonInvoiceDocument() {
        val quotation = PosHoldMapper.cartToDocument(cart())
            .copy(type = SalesDocumentType.QUOTATION)
        assertThrows(IllegalArgumentException::class.java) {
            PosHoldMapper.documentToCart(quotation, emptyList(), pkr)
        }
    }
}
