package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal

/**
 * Phase 22.5 — the customer profile's ledger view, pure.
 * This is the §17 profile's Transactions / Payments /
 * Statement content as READ VIEWS over party_ledger rows
 * (prep decision 4: no stored aggregates). It answers the
 * question 14.4 left open: PartyCredit computes
 * outstanding from the opening balance alone "until the
 * Phase 22 customer ledger exists" — this is that ledger
 * side. The screen layer (feature/parties' PartyProfile)
 * takes the built view as plain data, the same way
 * SalesScreen takes account options as params: no new
 * feature-to-feature dependency is introduced.
 *
 * Rules, all inherited from 22.3, not invented here:
 * - Receivable side only. PAYABLE rows are the vendor
 *   side and never reduce a receivable; rows with any
 *   other direction text are ignored, not misattributed.
 * - Currency is filtered, never summed across: a row in
 *   another currency is not part of this view.
 * - A negative net is a CREDIT BALANCE carried forward,
 *   not clamped — but outstanding (credit exposure) IS
 *   zero for a credit balance, mirroring 14.4's
 *   receivable-side-only rule: money owed back to the
 *   customer is not exposure.
 * - Row order is the caller's order. [AccountsViewModel]
 *   .loadLedgerRowsForParty already sorts by date then
 *   id, and the statement's running balance follows that
 *   order; this builder does not re-sort.
 */
object PartyLedgerProfile {

    data class StatementEntry(
        val row: PartyLedgerRecord,
        val runningBalance: Money,
    )

    data class Profile(
        val partyId: String,
        /** Net receivable balance, signed; negative = credit balance. */
        val balance: Money,
        /** Credit exposure: balance when positive, else zero. */
        val outstanding: Money,
        /** Receivable rows in caller order. */
        val transactions: List<PartyLedgerRecord>,
        /** Transactions with the running balance after each row. */
        val statement: List<StatementEntry>,
        /** Transactions whose document is a payment or refund. */
        val payments: List<PartyLedgerRecord>,
    )

    fun build(
        rows: List<PartyLedgerRecord>,
        partyId: String,
        currency: CurrencyCode,
    ): Profile {
        val mine = rows.filter { it.partyId == partyId }
            .filter { it.currency == currency.value }
            .filter { it.direction == "RECEIVABLE" }
        val balance = PartyLedgerBalance.receivableBalance(mine, partyId, currency)
        val outstanding = if (balance.isNegative) Money.zero(currency) else balance
        var running = BigDecimal.ZERO
        val statement = mine.map { row ->
            running = running.add(row.amount)
            StatementEntry(row, Money.of(running, currency))
        }
        val payments = mine.filter { it.documentType == "PAYMENT" || it.documentType == "REFUND" }
        return Profile(
            partyId = partyId,
            balance = balance,
            outstanding = outstanding,
            transactions = mine,
            statement = statement,
            payments = payments,
        )
    }
}
