package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal

/**
 * Phase 20.4 — the trial-balance preview: per-account debit
 * and credit totals across journal entries, plus grand
 * totals. This is a PREVIEW only — the report engine proper
 * stays Phase 24. The computation is pure (no repository,
 * no Android) so it is unit-tested here, not in the screen.
 *
 * Only accounts that appear in at least one line get a row;
 * rows follow the accounts order (code-sorted from the
 * ViewModel). An account id with no matching account record
 * resolves to the id itself, so unresolvable lines still
 * show instead of vanishing. Amounts are BigDecimals; the
 * screen renders them with toPlainString().
 */
data class TrialBalanceRow(
    val accountId: String,
    val accountName: String,
    val totalDebit: BigDecimal,
    val totalCredit: BigDecimal,
)

data class TrialBalance(
    val rows: List<TrialBalanceRow>,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal,
) {
    /** True when grand debits equal grand credits. */
    val isBalanced: Boolean
        get() = totalDebits.compareTo(totalCredits) == 0
}

fun trialBalance(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
): TrialBalance {
    val names = accounts.associate { it.id to it.name }
    val debitByAccount = mutableMapOf<String, BigDecimal>()
    val creditByAccount = mutableMapOf<String, BigDecimal>()
    var totalDebits = BigDecimal.ZERO
    var totalCredits = BigDecimal.ZERO
    entries.forEach { entry ->
        entry.lines.forEach { line ->
            debitByAccount[line.accountId] =
                (debitByAccount[line.accountId] ?: BigDecimal.ZERO) + line.debit
            creditByAccount[line.accountId] =
                (creditByAccount[line.accountId] ?: BigDecimal.ZERO) + line.credit
            totalDebits += line.debit
            totalCredits += line.credit
        }
    }
    val seenIds = (debitByAccount.keys + creditByAccount.keys).toSet()
    val rows = accounts
        .filter { it.id in seenIds }
        .map { account ->
            TrialBalanceRow(
                accountId = account.id,
                accountName = account.name,
                totalDebit = debitByAccount[account.id] ?: BigDecimal.ZERO,
                totalCredit = creditByAccount[account.id] ?: BigDecimal.ZERO,
            )
        } + seenIds
        .filter { id -> accounts.none { it.id == id } }
        .sorted()
        .map { id ->
            TrialBalanceRow(
                accountId = id,
                accountName = id,
                totalDebit = debitByAccount[id] ?: BigDecimal.ZERO,
                totalCredit = creditByAccount[id] ?: BigDecimal.ZERO,
            )
        }
    return TrialBalance(rows, totalDebits, totalCredits)
}

/**
 * Phase 24.1 — the trial-balance report: the 20.4 preview
 * over a date range. Entries are ISO yyyy-MM-dd text
 * (validated at write time), so lexicographic comparison
 * is chronological comparison. Both bounds are inclusive.
 * An inverted range is a caller error, not an empty
 * report.
 */
fun trialBalanceForPeriod(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
    fromDate: String,
    toDate: String,
): TrialBalance {
    require(fromDate <= toDate) { "fromDate must not be after toDate" }
    return trialBalance(
        entries = entries.filter { it.date in fromDate..toDate },
        accounts = accounts,
    )
}
