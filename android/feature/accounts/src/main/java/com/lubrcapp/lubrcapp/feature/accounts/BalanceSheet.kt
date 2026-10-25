package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal

/**
 * Phase 24.3 — the balance-sheet report as of a date. Asset
 * rows are ASSET accounts at net debit (debits − credits);
 * liability rows are LIABILITY accounts at net credit;
 * equity rows are EQUITY accounts at net credit. INCOME,
 * COST_OF_GOODS_SOLD, EXPENSE and OTHER_INCOME accounts
 * are profit & loss accounts and never appear here. Entries
 * are cumulative: every entry dated on or before asOfDate
 * counts (ISO yyyy-MM-dd, validated at write time). Lines
 * on accounts with no matching account record carry no §74
 * class and are excluded — the trial balance still shows
 * them, so nothing vanishes silently from the ledger view.
 *
 * There is deliberately no balanced flag: for a balanced
 * ledger, assets minus liabilities minus equity equals
 * undistributed profit, because profit & loss accounts are
 * excluded by design. The [BalanceSheet.difference] field
 * carries that amount, documented, never disguised as an
 * error.
 */
data class BalanceSheetRow(
    val accountId: String,
    val accountName: String,
    val type: AccountType,
    val amount: BigDecimal,
)

data class BalanceSheet(
    val assetRows: List<BalanceSheetRow>,
    val liabilityRows: List<BalanceSheetRow>,
    val equityRows: List<BalanceSheetRow>,
    val totalAssets: BigDecimal,
    val totalLiabilities: BigDecimal,
    val totalEquity: BigDecimal,
) {
    /**
     * Assets minus liabilities minus equity. For a balanced
     * ledger this equals undistributed profit (see above).
     */
    val difference: BigDecimal
        get() = totalAssets - totalLiabilities - totalEquity
}

fun balanceSheet(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
    asOfDate: String,
): BalanceSheet {
    val period = entries.filter { it.date <= asOfDate }
    val debitByAccount = mutableMapOf<String, BigDecimal>()
    val creditByAccount = mutableMapOf<String, BigDecimal>()
    period.forEach { entry ->
        entry.lines.forEach { line ->
            debitByAccount[line.accountId] =
                (debitByAccount[line.accountId] ?: BigDecimal.ZERO) + line.debit
            creditByAccount[line.accountId] =
                (creditByAccount[line.accountId] ?: BigDecimal.ZERO) + line.credit
        }
    }
    val seenIds = (debitByAccount.keys + creditByAccount.keys).toSet()
    val assetRows = mutableListOf<BalanceSheetRow>()
    val liabilityRows = mutableListOf<BalanceSheetRow>()
    val equityRows = mutableListOf<BalanceSheetRow>()
    accounts.filter { it.id in seenIds }.forEach { account ->
        val debit = debitByAccount[account.id] ?: BigDecimal.ZERO
        val credit = creditByAccount[account.id] ?: BigDecimal.ZERO
        when (account.type) {
            AccountType.ASSET ->
                assetRows += BalanceSheetRow(account.id, account.name, account.type, debit - credit)
            AccountType.LIABILITY ->
                liabilityRows += BalanceSheetRow(account.id, account.name, account.type, credit - debit)
            AccountType.EQUITY ->
                equityRows += BalanceSheetRow(account.id, account.name, account.type, credit - debit)
            else -> Unit
        }
    }
    val totalAssets = assetRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    val totalLiabilities = liabilityRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    val totalEquity = equityRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    return BalanceSheet(assetRows, liabilityRows, equityRows, totalAssets, totalLiabilities, totalEquity)
}
