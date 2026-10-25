package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSheetTest {

    private val accounts = listOf(
        AccountRecord("cash", "1000", "Cash", AccountType.ASSET),
        AccountRecord("loan", "2000", "Bank Loan", AccountType.LIABILITY),
        AccountRecord("capital", "3000", "Capital", AccountType.EQUITY),
        AccountRecord("sales", "4000", "Sales", AccountType.INCOME),
    )

    private fun entry(id: String, date: String, lines: List<JournalLineRecord>): JournalEntryRecord =
        JournalEntryRecord(id, date, "Entry", "REF-1", lines, "")

    @Test
    fun balanceSheetMathAndDifferenceEqualsProfit() {
        val sheet = balanceSheet(
            entries = listOf(
                entry(
                    "je-sale",
                    "2026-10-05",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
                        JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
                    ),
                ),
                entry(
                    "je-loan",
                    "2026-10-06",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("50000"), BigDecimal.ZERO),
                        JournalLineRecord("loan", BigDecimal.ZERO, BigDecimal("50000")),
                    ),
                ),
                entry(
                    "je-capital",
                    "2026-10-07",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("20000"), BigDecimal.ZERO),
                        JournalLineRecord("capital", BigDecimal.ZERO, BigDecimal("20000")),
                    ),
                ),
            ),
            accounts = accounts,
            asOfDate = "2026-10-31",
        )
        assertEquals(
            listOf(BalanceSheetRow("cash", "Cash", AccountType.ASSET, BigDecimal("170000"))),
            sheet.assetRows,
        )
        assertEquals(
            listOf(BalanceSheetRow("loan", "Bank Loan", AccountType.LIABILITY, BigDecimal("50000"))),
            sheet.liabilityRows,
        )
        assertEquals(
            listOf(BalanceSheetRow("capital", "Capital", AccountType.EQUITY, BigDecimal("20000"))),
            sheet.equityRows,
        )
        assertEquals(BigDecimal("170000"), sheet.totalAssets)
        assertEquals(BigDecimal("50000"), sheet.totalLiabilities)
        assertEquals(BigDecimal("20000"), sheet.totalEquity)
        assertEquals(BigDecimal("100000"), sheet.difference)
    }

    @Test
    fun entriesAfterAsOfDateAreExcluded() {
        val sheet = balanceSheet(
            entries = listOf(
                entry(
                    "je-oct",
                    "2026-10-15",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("10000"), BigDecimal.ZERO),
                        JournalLineRecord("capital", BigDecimal.ZERO, BigDecimal("10000")),
                    ),
                ),
                entry(
                    "je-nov",
                    "2026-11-01",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("99999"), BigDecimal.ZERO),
                        JournalLineRecord("capital", BigDecimal.ZERO, BigDecimal("99999")),
                    ),
                ),
            ),
            accounts = accounts,
            asOfDate = "2026-10-31",
        )
        assertEquals(BigDecimal("10000"), sheet.totalAssets)
        assertEquals(BigDecimal("10000"), sheet.totalEquity)
    }

    @Test
    fun unknownAccountLinesAreExcluded() {
        val sheet = balanceSheet(
            entries = listOf(
                entry(
                    "je-ghost",
                    "2026-10-15",
                    listOf(JournalLineRecord("ghost", BigDecimal("99999"), BigDecimal.ZERO)),
                ),
            ),
            accounts = accounts,
            asOfDate = "2026-10-31",
        )
        assertTrue(sheet.assetRows.isEmpty())
        assertTrue(sheet.liabilityRows.isEmpty())
        assertTrue(sheet.equityRows.isEmpty())
        assertEquals(BigDecimal.ZERO, sheet.totalAssets)
        assertEquals(BigDecimal.ZERO, sheet.difference)
    }

    @Test
    fun emptyLedgerProducesEmptySheet() {
        val sheet = balanceSheet(
            entries = emptyList(),
            accounts = accounts,
            asOfDate = "2026-10-31",
        )
        assertTrue(sheet.assetRows.isEmpty())
        assertEquals(BigDecimal.ZERO, sheet.totalAssets)
        assertEquals(BigDecimal.ZERO, sheet.totalLiabilities)
        assertEquals(BigDecimal.ZERO, sheet.totalEquity)
        assertEquals(BigDecimal.ZERO, sheet.difference)
    }
}
