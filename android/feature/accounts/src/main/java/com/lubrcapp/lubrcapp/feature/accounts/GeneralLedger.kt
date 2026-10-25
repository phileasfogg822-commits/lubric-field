package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal

/**
 * Phase 24.5 — the general ledger report: one account over
 * an inclusive ISO date range. The opening balance is the
 * net (debits − credits) of that account's lines in
 * entries dated before the range; each movement is one
 * entry in the range, with that entry's lines on the
 * account summed (an entry may touch the same account
 * more than once), chronological by date then entry id,
 * each carrying its running balance; the closing balance
 * is the last running balance (or the opening balance
 * when there are no movements). Balances are plain
 * BigDecimals: journal lines carry no currency, so the
 * ledger is currency-agnostic like the trial balance.
 * An inverted range is a caller error.
 */
data class GeneralLedgerMovement(
    val entryId: String,
    val date: String,
    val description: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val runningBalance: BigDecimal,
)

data class GeneralLedger(
    val accountId: String,
    val accountName: String,
    val fromDate: String,
    val toDate: String,
    val openingBalance: BigDecimal,
    val movements: List<GeneralLedgerMovement>,
    val closingBalance: BigDecimal,
)

fun generalLedger(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
    accountId: String,
    fromDate: String,
    toDate: String,
): GeneralLedger {
    require(fromDate <= toDate) { "fromDate must not be after toDate" }
    val accountName = accounts.firstOrNull { it.id == accountId }?.name ?: accountId
    fun accountNet(entry: JournalEntryRecord): Pair<BigDecimal, BigDecimal> {
        val debit = entry.lines
            .filter { it.accountId == accountId }
            .fold(BigDecimal.ZERO) { acc, line -> acc + line.debit }
        val credit = entry.lines
            .filter { it.accountId == accountId }
            .fold(BigDecimal.ZERO) { acc, line -> acc + line.credit }
        return debit to credit
    }
    val opening = entries
        .filter { it.date < fromDate }
        .fold(BigDecimal.ZERO) { acc, entry ->
            val (debit, credit) = accountNet(entry)
            acc + debit - credit
        }
    var running = opening
    val movements = entries
        .filter { it.date in fromDate..toDate }
        .filter { entry -> entry.lines.any { it.accountId == accountId } }
        .sortedWith(compareBy({ it.date }, { it.id }))
        .map { entry ->
            val (debit, credit) = accountNet(entry)
            running += debit - credit
            GeneralLedgerMovement(
                entryId = entry.id,
                date = entry.date,
                description = entry.description,
                debit = debit,
                credit = credit,
                runningBalance = running,
            )
        }
    return GeneralLedger(
        accountId = accountId,
        accountName = accountName,
        fromDate = fromDate,
        toDate = toDate,
        openingBalance = opening,
        movements = movements,
        closingBalance = running,
    )
}
