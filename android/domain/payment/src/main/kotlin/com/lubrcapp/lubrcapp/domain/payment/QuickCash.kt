package com.lubrcapp.lubrcapp.domain.payment

import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal

/**
 * Phase 26.3 — quick cash payment (§150): buttons Exact /
 * 500 / 1000 / 5000 / Other; change calculated
 * automatically. Pure tender math only: no posting, no
 * persistence — the actual payment rides the 22.x posting
 * machinery at Pay (26.7).
 *
 * Denominations are values in the total's currency, not
 * currency-aware amounts: the spec's 500 / 1000 / 5000 are
 * examples, and currency wiring is still shell work, so
 * the caller supplies the denomination set (defaulting to
 * the spec examples). The Exact button is always the total
 * itself; "Other" is free input on the screen, not a model
 * value. Denominations below the total are kept, not
 * filtered: tapping one tenders a partial amount and the
 * settlement reports the remaining due.
 */
sealed interface CashSettlement {
    /** Tendered covers the total; change may be zero. */
    data class Paid(val change: Money) : CashSettlement

    /** Tendered falls short; remaining is still due. */
    data class Shortfall(val remaining: Money) : CashSettlement
}

object QuickCash {

    val DEFAULT_DENOMINATIONS: List<BigDecimal> = listOf(
        BigDecimal("500"),
        BigDecimal("1000"),
        BigDecimal("5000"),
    )

    fun settle(total: Money, tendered: Money): CashSettlement {
        // Money.minus throws on currency mismatch, as it should.
        val difference = tendered - total
        return if (difference.isNegative) {
            CashSettlement.Shortfall(-difference)
        } else {
            CashSettlement.Paid(difference)
        }
    }

    /** Button amounts: Exact first, then each denomination in the total's currency. */
    fun buttonAmounts(
        total: Money,
        denominations: List<BigDecimal> = DEFAULT_DENOMINATIONS,
    ): List<Money> =
        listOf(total) + denominations.map { Money.of(it, total.currency) }
}
