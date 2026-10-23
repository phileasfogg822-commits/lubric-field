package com.lubrcapp.lubrcapp.feature.parties

import com.lubrcapp.lubrcapp.domain.party.PartyType

/**
 * Phase 14.2 — how a party's opening balance reads in the list.
 *
 * Until the Phase 22 customer ledger exists, the opening balance is the
 * only balance fact (see the phase gates doc). Its direction follows
 * the party type and the amount's sign:
 *
 * - Customer / Both: a positive opening balance is money the party owes
 *   the business (Receivable); a negative one is an advance the business
 *   holds for the party (Payable).
 * - Supplier: mirrored — positive is money the business owes the
 *   supplier (Payable); negative is an advance paid to the supplier
 *   (Receivable).
 * - Other: no direction is implied; the amount is shown neutrally.
 *
 * Zero or absent opening balances produce no balance line at all.
 */
enum class OpeningBalanceDirection {
    RECEIVABLE,
    PAYABLE,
    NEUTRAL,
}

/** The balance-line direction for this party, or null when no line should show. */
fun Party.openingBalanceDirection(): OpeningBalanceDirection? {
    val balance = openingBalance ?: return null
    if (balance.signum() == 0) return null
    return when (type) {
        PartyType.CUSTOMER, PartyType.BOTH ->
            if (balance.signum() > 0) OpeningBalanceDirection.RECEIVABLE else OpeningBalanceDirection.PAYABLE
        PartyType.SUPPLIER ->
            if (balance.signum() > 0) OpeningBalanceDirection.PAYABLE else OpeningBalanceDirection.RECEIVABLE
        PartyType.OTHER -> OpeningBalanceDirection.NEUTRAL
    }
}
