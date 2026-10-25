package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal

/**
 * Phase 24.2 — the profit & loss report over a date range.
 * Income rows: INCOME + OTHER_INCOME accounts, amount = net
 * credit (credits − debits). Expense rows:
 * COST_OF_GOODS_SOLD + EXPENSE accounts, amount = net debit
 * (debits − credits). ASSET, LIABILITY and EQUITY accounts
 * are balance-sheet accounts and never appear here. Lines
 * on accounts with no matching account record carry no §74
 * class and are excluded — the trial balance still shows
 * them, so nothing vanishes silently from the ledger view.
 * Bounds are ISO yyyy-MM-dd, inclusive; an inverted range
 * is a caller error. Net profit may be negative (a net
 * loss); it is never clamped.
 */
data class ProfitAndLossRow(
    val accountId: String,
    val accountName: String,
    val type: AccountType,
    val amount: BigDecimal,
)

data class ProfitAndLoss(
    val incomeRows: List<ProfitAndLossRow>,
    val expenseRows: List<ProfitAndLossRow>,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
) {
    /** Net profit; negative means a net loss. Never clamped. */
    val netProfit: BigDecimal
        get() = totalIncome - totalExpenses
}

fun profitAndLoss(
    entries: List<JournalEntryRecord>,
    accounts: List<AccountRecord>,
    fromDate: String,
    toDate: String,
): ProfitAndLoss {
    require(fromDate <= toDate) { "fromDate must not be after toDate" }
    val period = entries.filter { it.date in fromDate..toDate }
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
    val incomeRows = mutableListOf<ProfitAndLossRow>()
    val expenseRows = mutableListOf<ProfitAndLossRow>()
    accounts.filter { it.id in seenIds }.forEach { account ->
        val debit = debitByAccount[account.id] ?: BigDecimal.ZERO
        val credit = creditByAccount[account.id] ?: BigDecimal.ZERO
        when (account.type) {
            AccountType.INCOME, AccountType.OTHER_INCOME ->
                incomeRows += ProfitAndLossRow(account.id, account.name, account.type, credit - debit)
            AccountType.COST_OF_GOODS_SOLD, AccountType.EXPENSE ->
                expenseRows += ProfitAndLossRow(account.id, account.name, account.type, debit - credit)
            else -> Unit
        }
    }
    val totalIncome = incomeRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    val totalExpenses = expenseRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    return ProfitAndLoss(incomeRows, expenseRows, totalIncome, totalExpenses)
}
