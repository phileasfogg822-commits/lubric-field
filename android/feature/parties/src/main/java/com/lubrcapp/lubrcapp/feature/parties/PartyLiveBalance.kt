package com.lubrcapp.lubrcapp.feature.parties

import java.math.BigDecimal

/**
 * Phase 32.2 — a party's live balance split for the khata list, in
 * plain amounts (the screen displays text, it never formats Money).
 *
 * Both directions net separately, then cross-direction credits fold
 * in: a negative receivable net is money owed back to the customer
 * (a payable), and a negative payable net is an advance the supplier
 * owes the business (a receivable). A party with no rows is zero on
 * both sides — never an error.
 */
data class PartyLiveBalance(
    val receivable: BigDecimal,
    val payable: BigDecimal,
)

fun combineLiveBalance(receivableNet: BigDecimal, payableNet: BigDecimal): PartyLiveBalance {
    val receivable = (
        if (receivableNet.signum() > 0) receivableNet else BigDecimal.ZERO
        ).add(if (payableNet.signum() < 0) payableNet.negate() else BigDecimal.ZERO)
    val payable = (
        if (payableNet.signum() > 0) payableNet else BigDecimal.ZERO
        ).add(if (receivableNet.signum() < 0) receivableNet.negate() else BigDecimal.ZERO)
    return PartyLiveBalance(receivable, payable)
}
