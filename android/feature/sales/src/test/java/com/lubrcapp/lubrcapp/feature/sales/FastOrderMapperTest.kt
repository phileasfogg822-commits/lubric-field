package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
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
import org.junit.Assert.assertTrue
import org.junit.Test

class FastOrderMapperTest {

    private val currency = CurrencyCode("PKR")

    private fun namedCart(): PosCart {
        val cart = PosCart(id = "order-1", party = PosParty.Named(PartyId("ali-autos")))
        val withLines = cart.copy(
            lines = listOf(
                PosCartLine(
                    id = "l1",
                    description = "Engine Oil",
                    quantity = Quantity.of("2"),
                    rate = Money.of("500", currency),
                    discount = PosLineDiscount.Percent(Percentage.of("10")),
                ),
                PosCartLine(
                    id = "l2",
                    description = "Filter",
                    quantity = Quantity.of("1"),
                    rate = Money.of("300", currency),
                    discount = PosLineDiscount.Fixed(Money.of("50", currency)),
                ),
            ),
        )
        return withLines
    }

    @Test
    fun cartToOrderMapsNamedPartyToDraftSalesOrder() {
        val order = FastOrderMapper.cartToOrder(namedCart())
        assertEquals(SalesDocumentType.SALES_ORDER, order.type)
        assertEquals("DRAFT", order.status)
        assertEquals("ali-autos", order.partyId)
        assertEquals("", order.number)
        assertEquals("", order.date)
    }

    @Test
    fun cartToOrderThrowsOnWalkIn() {
        assertThrows(IllegalArgumentException::class.java) {
            FastOrderMapper.cartToOrder(PosCart(id = "order-1"))
        }
    }

    @Test
    fun cartToLinesKeepsIdsPositionsAndDiscounts() {
        val lines = FastOrderMapper.cartToLines(namedCart())
        assertEquals(listOf("l1", "l2"), lines.map { it.id })
        assertEquals(listOf(0, 1), lines.map { it.position })
        assertEquals("PERCENT", lines[0].discountKind)
        assertEquals("FIXED", lines[1].discountKind)
        assertTrue(lines.all { it.orderId == "order-1" })
    }

    @Test
    fun orderToCartRequiresSalesOrder() {
        val invoice = SalesDocument(id = "x", type = SalesDocumentType.INVOICE, status = "DRAFT")
        assertThrows(IllegalArgumentException::class.java) {
            FastOrderMapper.orderToCart(invoice, emptyList(), currency)
        }
    }

    @Test
    fun orderToCartRestoresNamedPartyAndSortsLines() {
        val cart = namedCart()
        val order = FastOrderMapper.cartToOrder(cart)
        val lines = FastOrderMapper.cartToLines(cart).reversed()
        val restored = FastOrderMapper.orderToCart(order, lines, currency)
        assertEquals(PosParty.Named(PartyId("ali-autos")), restored.party)
        assertEquals(listOf("l1", "l2"), restored.lines.map { it.id })
    }

    @Test
    fun orderToCartMapsBlankPartyToWalkIn() {
        val order = SalesDocument(id = "x", type = SalesDocumentType.SALES_ORDER, status = "DRAFT")
        val restored = FastOrderMapper.orderToCart(order, emptyList(), currency)
        assertEquals(PosParty.WalkIn, restored.party)
    }

    @Test
    fun roundTripPreservesCart() {
        val cart = namedCart()
        val order = FastOrderMapper.cartToOrder(cart)
        val restored = FastOrderMapper.orderToCart(order, FastOrderMapper.cartToLines(cart), currency)
        assertEquals(cart, restored)
    }

    @Test
    fun roundTripPreservesDiscountValues() {
        val cart = namedCart()
        val order = FastOrderMapper.cartToOrder(cart)
        val restored = FastOrderMapper.orderToCart(order, FastOrderMapper.cartToLines(cart), currency)
        val percent = restored.lines[0].discount as PosLineDiscount.Percent
        assertEquals(Percentage.of("10"), percent.percentage)
        val fixed = restored.lines[1].discount as PosLineDiscount.Fixed
        assertEquals(Money.of("50", currency), fixed.amount)
    }
}
