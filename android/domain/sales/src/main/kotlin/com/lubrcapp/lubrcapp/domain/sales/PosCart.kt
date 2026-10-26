package com.lubrcapp.lubrcapp.domain.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import java.math.RoundingMode

/**
 * Phase 26.1 — the POS cart (§147 POS Mode, §149 Walk-In
 * Customer). Pure and immutable: every operation returns a
 * new cart, so the counter screen, hold (26.2), and checkout
 * all share one state shape without a ViewModel.
 *
 * Amounts are NOT stored totals: [PosCartOps.lineAmount]
 * and [PosCartOps.subtotal] are pre-tax, computed from the
 * lines each time — the same rule as the sales engine
 * (totals are recomputed, never stored as truth). Tax is
 * an invoice concern, applied at conversion to a sales
 * document, not in the cart. No persistence here: hold
 * (26.2) saves the cart as a DRAFT sales document
 * through the existing record machinery.
 *
 * Party: a cart is WalkIn by default (§149 — no customer
 * creation required); naming a party replaces the
 * default, clearing it returns to WalkIn.
 */
sealed interface PosParty {
    data object WalkIn : PosParty
    data class Named(val id: PartyId) : PosParty
}

sealed interface PosLineDiscount {
    data class Percent(val percentage: Percentage) : PosLineDiscount
    data class Fixed(val amount: Money) : PosLineDiscount
}

data class PosCartLine(
    val id: String,
    val itemId: ItemId? = null,
    val description: String = "",
    val quantity: Quantity,
    val rate: Money,
    val discount: PosLineDiscount? = null,
)

data class PosCart(
    val id: String,
    val party: PosParty = PosParty.WalkIn,
    val lines: List<PosCartLine> = emptyList(),
)

object PosCartOps {

    /**
     * Scanning the same item twice increments its quantity
     * instead of adding a second row — but only when the
     * existing row carries the same rate and discount.
     * A different rate or discount appends a separate row,
     * and lines without an item id never merge.
     */
    fun addLine(cart: PosCart, line: PosCartLine): PosCart {
        require(line.quantity.isPositive) { "Cart quantity must be positive" }
        val mergeIndex = cart.lines.indexOfFirst { existing ->
            line.itemId != null &&
                existing.itemId == line.itemId &&
                existing.rate == line.rate &&
                existing.discount == line.discount
        }
        if (mergeIndex < 0) return cart.copy(lines = cart.lines + line)
        val merged = cart.lines[mergeIndex].copy(
            quantity = cart.lines[mergeIndex].quantity + line.quantity,
        )
        return cart.copy(
            lines = cart.lines.toMutableList().also { it[mergeIndex] = merged },
        )
    }

    fun updateQuantity(cart: PosCart, lineId: String, quantity: Quantity): PosCart {
        require(quantity.isPositive) { "Cart quantity must be positive" }
        val index = cart.lines.indexOfFirst { it.id == lineId }
        require(index >= 0) { "No cart line with id $lineId" }
        return cart.copy(
            lines = cart.lines.toMutableList().also {
                it[index] = it[index].copy(quantity = quantity)
            },
        )
    }

    fun removeLine(cart: PosCart, lineId: String): PosCart {
        require(cart.lines.any { it.id == lineId }) { "No cart line with id $lineId" }
        return cart.copy(lines = cart.lines.filterNot { it.id == lineId })
    }

    fun applyDiscount(cart: PosCart, lineId: String, discount: PosLineDiscount): PosCart {
        val index = cart.lines.indexOfFirst { it.id == lineId }
        require(index >= 0) { "No cart line with id $lineId" }
        return cart.copy(
            lines = cart.lines.toMutableList().also {
                it[index] = it[index].copy(discount = discount)
            },
        )
    }

    fun removeDiscount(cart: PosCart, lineId: String): PosCart {
        val index = cart.lines.indexOfFirst { it.id == lineId }
        require(index >= 0) { "No cart line with id $lineId" }
        return cart.copy(
            lines = cart.lines.toMutableList().also {
                it[index] = it[index].copy(discount = null)
            },
        )
    }

    fun setParty(cart: PosCart, party: PosParty): PosCart = cart.copy(party = party)

    /**
     * Pre-tax line amount: gross (rate x quantity, rounded
     * 2dp HALF_UP per line, mirroring the totals engine)
     * minus the discount, clamped at zero — a discount
     * never makes a line negative.
     */
    fun lineAmount(line: PosCartLine): Money {
        val gross = (line.rate * line.quantity.amount).rounded(2, RoundingMode.HALF_UP)
        val discountAmount = when (val discount = line.discount) {
            null -> Money.zero(line.rate.currency)
            is PosLineDiscount.Percent ->
                discount.percentage.applyTo(gross).rounded(2, RoundingMode.HALF_UP)
            is PosLineDiscount.Fixed -> discount.amount
        }
        val net = gross - discountAmount
        return if (net.isNegative) Money.zero(line.rate.currency) else net
    }

    /**
     * Pre-tax cart total: the sum of line amounts. An empty
     * cart totals zero in the given currency; mixed
     * currencies fail on Money.plus, as they should.
     */
    fun subtotal(cart: PosCart, currency: CurrencyCode): Money =
        cart.lines.fold(Money.zero(currency)) { acc, line -> acc + lineAmount(line) }
}
