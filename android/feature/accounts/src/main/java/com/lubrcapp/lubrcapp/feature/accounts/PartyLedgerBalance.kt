package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal

/**
 * Phase 22.3 — pure party balances over ledger rows.
 * Outstanding is the NET SUM of signed RECEIVABLE effects
 * per party per currency: invoices add, credit notes and
 * payments subtract. A negative net is a CREDIT BALANCE
 * carried forward, NOT clamped to zero — clamping would
 * erase money owed back to the customer. PAYABLE rows are
 * a separate balance (the vendor side) and never reduce
 * a receivable. Rows with any other direction text are
 * ignored, not misattributed. No rows, or no matching
 * rows, is a zero balance — never an error.
 */
object PartyLedgerBalance {

    fun receivableBalance(
        rows: List<PartyLedgerRecord>,
        partyId: String,
        currency: CurrencyCode,
    ): Money = balance(rows, partyId, currency, "RECEIVABLE")

    fun payableBalance(
        rows: List<PartyLedgerRecord>,
        partyId: String,
        currency: CurrencyCode,
    ): Money = balance(rows, partyId, currency, "PAYABLE")

    private fun balance(
        rows: List<PartyLedgerRecord>,
        partyId: String,
        currency: CurrencyCode,
        direction: String,
    ): Money {
        val sum = rows
            .filter { it.partyId == partyId }
            .filter { it.currency == currency.value }
            .filter { it.direction == direction }
            .fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }
        return Money.of(sum, currency)
    }
}
