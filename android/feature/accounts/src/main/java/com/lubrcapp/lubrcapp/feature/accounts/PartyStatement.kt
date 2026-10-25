package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import java.math.BigDecimal

/**
 * Phase 24.4 — the party statement report: one party, one
 * direction (RECEIVABLE or PAYABLE) and one currency over
 * an inclusive ISO date range. The opening balance is the
 * net sum of matching rows dated before the range; the
 * movements are the matching rows inside the range, in
 * chronological order; the closing balance is opening plus
 * the period net. Amounts are signed (22.3 rule), so
 * invoices add and credit notes and payments subtract.
 * Balances are Money, per currency — never summed across
 * currencies. A direction other than RECEIVABLE/PAYABLE
 * matches nothing (the 22.3 rule: unknown directions are
 * ignored, not misattributed). Bounds are ISO yyyy-MM-dd,
 * inclusive; an inverted range is a caller error.
 */
data class PartyStatement(
    val partyId: String,
    val direction: String,
    val currency: CurrencyCode,
    val fromDate: String,
    val toDate: String,
    val openingBalance: Money,
    val movements: List<PartyLedgerRecord>,
    val closingBalance: Money,
)

fun partyStatement(
    rows: List<PartyLedgerRecord>,
    partyId: String,
    direction: String,
    currency: CurrencyCode,
    fromDate: String,
    toDate: String,
): PartyStatement {
    require(fromDate <= toDate) { "fromDate must not be after toDate" }
    val matching = rows.filter {
        it.partyId == partyId && it.direction == direction && it.currency == currency.value
    }
    val opening = matching
        .filter { it.date < fromDate }
        .fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    val movements = matching
        .filter { it.date in fromDate..toDate }
        .sortedWith(compareBy({ it.date }, { it.id }))
    val periodNet = movements.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    return PartyStatement(
        partyId = partyId,
        direction = direction,
        currency = currency,
        fromDate = fromDate,
        toDate = toDate,
        openingBalance = Money.of(opening, currency),
        movements = movements,
        closingBalance = Money.of(opening + periodNet, currency),
    )
}
