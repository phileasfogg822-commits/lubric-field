package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialBalanceTest {

    private val accounts = listOf(
        AccountRecord("cash", "1000", "Cash", AccountType.ASSET),
        AccountRecord("sales", "4000", "Sales", AccountType.INCOME),
    )

    private fun entry(lines: List<JournalLineRecord>): JournalEntryRecord =
        JournalEntryRecord("je-1", "2026-10-24", "Sale", "INV-1", lines, "")

    @Test
    fun balancedEntriesProduceBalancedTrialBalance() {
        val balance = trialBalance(
            entries = listOf(
                entry(
                    listOf(
                        JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
                        JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
                    ),
                ),
            ),
            accounts = accounts,
        )
        assertTrue(balance.isBalanced)
        assertEquals(BigDecimal("100000"), balance.totalDebits)
        assertEquals(BigDecimal("100000"), balance.totalCredits)
        assertEquals(
            listOf(
                TrialBalanceRow("cash", "Cash", BigDecimal("100000"), BigDecimal.ZERO),
                TrialBalanceRow("sales", "Sales", BigDecimal.ZERO, BigDecimal("100000")),
            ),
            balance.rows,
        )
    }

    @Test
    fun unbalancedEntriesReportOutOfBalance() {
        val balance = trialBalance(
            entries = listOf(
                entry(
                    listOf(
                        JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
                        JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("99999")),
                    ),
                ),
            ),
            accounts = accounts,
        )
        assertFalse(balance.isBalanced)
        assertEquals(BigDecimal("100000"), balance.totalDebits)
        assertEquals(BigDecimal("99999"), balance.totalCredits)
    }

    @Test
    fun unknownAccountIdFallsBackToItsId() {
        val balance = trialBalance(
            entries = listOf(
                entry(listOf(JournalLineRecord("ghost", BigDecimal("50"), BigDecimal.ZERO))),
            ),
            accounts = accounts,
        )
        assertEquals(
            listOf(TrialBalanceRow("ghost", "ghost", BigDecimal("50"), BigDecimal.ZERO)),
            balance.rows,
        )
    }

    @Test
    fun emptyEntriesProduceEmptyBalancedTrialBalance() {
        val balance = trialBalance(emptyList(), accounts)
        assertTrue(balance.isBalanced)
        assertEquals(emptyList<TrialBalanceRow>(), balance.rows)
        assertEquals(BigDecimal.ZERO, balance.totalDebits)
        assertEquals(BigDecimal.ZERO, balance.totalCredits)
    }
}
