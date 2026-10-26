package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PosCartTest {

    private val pkr = CurrencyCode("PKR")
    private val usd = CurrencyCode("USD")

    private fun line(
        id: String,
        itemId: String? = "item-1",
        quantity: String = "1",
        rate: String = "100",
        currency: CurrencyCode = pkr,
        discount: PosLineDiscount? = null,
    ) = PosCartLine(
        id = id,
        itemId = itemId?.let { ItemId(it) },
        description = "Item $id",
        quantity = Quantity.of(quantity),
        rate = Money.of(rate, currency),
        discount = discount,
    )

    @Test
    fun emptyCartHasWalkInPartyAndZeroSubtotal() {
        val cart = PosCart(id = "cart-1")
        assertEquals(PosParty.WalkIn, cart.party)
        assertTrue(cart.lines.isEmpty())
        assertEquals(Money.zero(pkr), PosCartOps.subtotal(cart, pkr))
    }

    @Test
    fun addLineAppendsRow() {
        val cart = PosCartOps.addLine(PosCart(id = "cart-1"), line("l1"))
        assertEquals(1, cart.lines.size)
        assertEquals("l1", cart.lines[0].id)
    }

    @Test
    fun addLineMergesSameItemSameRate() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "2")) }
            .let { PosCartOps.addLine(it, line("l2", quantity = "3")) }
        assertEquals(1, cart.lines.size)
        assertEquals(Quantity.of("5"), cart.lines[0].quantity)
    }

    @Test
    fun addLineAppendsOnDifferentRate() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", rate = "100")) }
            .let { PosCartOps.addLine(it, line("l2", rate = "90")) }
        assertEquals(2, cart.lines.size)
    }

    @Test
    fun addLineNeverMergesUnidentifiedLines() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", itemId = null)) }
            .let { PosCartOps.addLine(it, line("l2", itemId = null)) }
        assertEquals(2, cart.lines.size)
    }

    @Test
    fun addLineRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            PosCartOps.addLine(PosCart(id = "cart-1"), line("l1", quantity = "0"))
        }
    }

    @Test
    fun updateQuantityChangesQuantity() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "1")) }
            .let { PosCartOps.updateQuantity(it, "l1", Quantity.of("4")) }
        assertEquals(Quantity.of("4"), cart.lines[0].quantity)
    }

    @Test
    fun updateQuantityRejectsNonPositiveAndUnknownLine() {
        val cart = PosCartOps.addLine(PosCart(id = "cart-1"), line("l1"))
        assertThrows(IllegalArgumentException::class.java) {
            PosCartOps.updateQuantity(cart, "l1", Quantity.of("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PosCartOps.updateQuantity(cart, "missing", Quantity.of("2"))
        }
    }

    @Test
    fun removeLineDropsRow() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1")) }
            .let { PosCartOps.addLine(it, line("l2", itemId = "item-2")) }
            .let { PosCartOps.removeLine(it, "l1") }
        assertEquals(1, cart.lines.size)
        assertEquals("l2", cart.lines[0].id)
    }

    @Test
    fun percentDiscountReducesLineAmount() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "2", rate = "100")) }
            .let {
                PosCartOps.applyDiscount(
                    it, "l1",
                    PosLineDiscount.Percent(Percentage.of("10")),
                )
            }
        assertEquals(Money.of("180", pkr), PosCartOps.lineAmount(cart.lines[0]))
    }

    @Test
    fun fixedDiscountClampsLineAmountAtZero() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "1", rate = "30")) }
            .let {
                PosCartOps.applyDiscount(
                    it, "l1",
                    PosLineDiscount.Fixed(Money.of("50", pkr)),
                )
            }
        assertEquals(Money.zero(pkr), PosCartOps.lineAmount(cart.lines[0]))
    }

    @Test
    fun removeDiscountRestoresGross() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "1", rate = "100")) }
            .let {
                PosCartOps.applyDiscount(
                    it, "l1",
                    PosLineDiscount.Percent(Percentage.of("25")),
                )
            }
            .let { PosCartOps.removeDiscount(it, "l1") }
        assertEquals(Money.of("100", pkr), PosCartOps.lineAmount(cart.lines[0]))
    }

    @Test
    fun setPartyNamedAndBackToWalkIn() {
        val named = PosCartOps.setParty(
            PosCart(id = "cart-1"),
            PosParty.Named(PartyId("party-1")),
        )
        assertEquals(PosParty.Named(PartyId("party-1")), named.party)
        assertEquals(PosParty.WalkIn, PosCartOps.setParty(named, PosParty.WalkIn).party)
    }

    @Test
    fun subtotalSumsDiscountedLines() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", quantity = "2", rate = "100")) }
            .let { PosCartOps.addLine(it, line("l2", itemId = "item-2", quantity = "1", rate = "50")) }
        assertEquals(Money.of("250", pkr), PosCartOps.subtotal(cart, pkr))
    }

    @Test
    fun subtotalMixedCurrencyThrows() {
        val cart = PosCart(id = "cart-1")
            .let { PosCartOps.addLine(it, line("l1", rate = "100", currency = pkr)) }
            .let {
                PosCartOps.addLine(
                    it, line("l2", itemId = "item-2", rate = "10", currency = usd),
                )
            }
        assertThrows(IllegalArgumentException::class.java) {
            PosCartOps.subtotal(cart, pkr)
        }
    }
}
