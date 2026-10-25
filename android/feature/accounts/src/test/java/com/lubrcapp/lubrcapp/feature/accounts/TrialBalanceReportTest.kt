package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialBalanceReportTest {

    private val accounts = listOf(
        AccountRecord("cash", "1000", "Cash", AccountType.ASSET),
        AccountRecord("sales", "4000", "Sales", AccountType.INCOME),
    )

    private fun entry(id: String, date: String, lines: List<JournalLineRecord>): JournalEntryRecord =
        JournalEntryRecord(id, date, "Sale", "INV-1", lines, "")

    private val saleLines = listOf(
        JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
        JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
    )

    @Test
    fun entriesOutsideRangeAreExcluded() {
        val balance = trialBalanceForPeriod(
            entries = listOf(
                entry("je-before", "2026-09-30", saleLines),
                entry("je-in", "2026-10-15", saleLines),
                entry("je-after", "2026-11-01", saleLines),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertTrue(balance.isBalanced)
        assertEquals(BigDecimal("100000"), balance.totalDebits)
        assertEquals(BigDecimal("100000"), balance.totalCredits)
        assertTrue(balance.rows.size == 2)
    }

    @Test
    fun boundaryDatesAreInclusive() {
        val balance = trialBalanceForPeriod(
            entries = listOf(
                entry("je-from", "2026-10-01", saleLines),
                entry("je-to", "2026-10-31", saleLines),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(BigDecimal("200000"), balance.totalDebits)
        assertEquals(BigDecimal("200000"), balance.totalCredits)
    }

    @Test
    fun invertedRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            trialBalanceForPeriod(
                entries = emptyList(),
                accounts = accounts,
                fromDate = "2026-10-31",
                toDate = "2026-10-01",
            )
        }
    }

    @Test
    fun emptyRangeProducesEmptyBalancedReport() {
        val balance = trialBalanceForPeriod(
            entries = listOf(entry("je-in", "2026-10-15", saleLines)),
            accounts = accounts,
            fromDate = "2026-11-01",
            toDate = "2026-11-30",
        )
        assertTrue(balance.isBalanced)
        assertEquals(emptyList<TrialBalanceRow>(), balance.rows)
        assertEquals(BigDecimal.ZERO, balance.totalDebits)
        assertEquals(BigDecimal.ZERO, balance.totalCredits)
    }
}
