package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerRecord
import com.lubrcapp.lubrcapp.feature.dashboard.HomeSummary
import com.lubrcapp.lubrcapp.feature.parties.PartiesViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.first

/**
 * Phase 33.2 — Home money overview. The summing rule is the
 * PartyLedgerBalance.balance rule generalized across parties:
 * signed amounts, filtered to one currency and one direction.
 * The currency is the first non-blank currency among the loaded
 * rows (the 32.2 earliest-row rule), else "XXX".
 */
fun summarizeHomeLedger(rows: List<PartyLedgerRecord>): HomeSummary {
    val currency = rows.firstOrNull { it.currency.isNotBlank() }?.currency ?: "XXX"
    fun total(direction: String): BigDecimal =
        rows
            .filter { it.currency == currency && it.direction == direction }
            .fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }
    return HomeSummary(
        receivable = total("RECEIVABLE"),
        payable = total("PAYABLE"),
        currency = currency,
    )
}

/**
 * Loads every party's ledger rows for the scope and summarizes
 * them. Returns null when the business has no parties yet, so
 * Home hides the money card instead of showing empty zeros.
 */
suspend fun loadHomeSummary(
    scope: OperationalScope,
    partiesViewModel: PartiesViewModel,
    accountsViewModel: AccountsViewModel,
): HomeSummary? {
    val parties = partiesViewModel.observeParties(scope).first()
    if (parties.isEmpty()) return null
    val rows = parties.flatMap { party ->
        accountsViewModel.loadLedgerRowsForParty(scope, party.id)
    }
    return summarizeHomeLedger(rows)
}
