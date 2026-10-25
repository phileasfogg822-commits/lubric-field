package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitAndLossTest {

    private val accounts = listOf(
        AccountRecord("cash", "1000", "Cash", AccountType.ASSET),
        AccountRecord("sales", "4000", "Sales", AccountType.INCOME),
        AccountRecord("interest", "4100", "Interest", AccountType.OTHER_INCOME),
        AccountRecord("cogs", "5000", "COGS", AccountType.COST_OF_GOODS_SOLD),
        AccountRecord("rent", "6000", "Rent", AccountType.EXPENSE),
    )

    private fun entry(id: String, date: String, lines: List<JournalLineRecord>): JournalEntryRecord =
        JournalEntryRecord(id, date, "Entry", "REF-1", lines, "")

    private fun saleOn(date: String): JournalEntryRecord =
        entry(
            "je-sale-$date",
            date,
            listOf(
                JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
                JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
            ),
        )

    @Test
    fun profitMathNetsIncomeAndExpenses() {
        val pnl = profitAndLoss(
            entries = listOf(
                saleOn("2026-10-05"),
                entry(
                    "je-return",
                    "2026-10-06",
                    listOf(
                        JournalLineRecord("sales", BigDecimal("10000"), BigDecimal.ZERO),
                        JournalLineRecord("cash", BigDecimal.ZERO, BigDecimal("10000")),
                    ),
                ),
                entry(
                    "je-interest",
                    "2026-10-07",
                    listOf(
                        JournalLineRecord("cash", BigDecimal("5000"), BigDecimal.ZERO),
                        JournalLineRecord("interest", BigDecimal.ZERO, BigDecimal("5000")),
                    ),
                ),
                entry(
                    "je-cogs",
                    "2026-10-08",
                    listOf(
                        JournalLineRecord("cogs", BigDecimal("20000"), BigDecimal.ZERO),
                        JournalLineRecord("cash", BigDecimal.ZERO, BigDecimal("20000")),
                    ),
                ),
                entry(
                    "je-rent",
                    "2026-10-09",
                    listOf(
                        JournalLineRecord("rent", BigDecimal("30000"), BigDecimal.ZERO),
                        JournalLineRecord("cash", BigDecimal.ZERO, BigDecimal("30000")),
                    ),
                ),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(
            listOf(
                ProfitAndLossRow("sales", "Sales", AccountType.INCOME, BigDecimal("90000")),
                ProfitAndLossRow("interest", "Interest", AccountType.OTHER_INCOME, BigDecimal("5000")),
            ),
            pnl.incomeRows,
        )
        assertEquals(
            listOf(
                ProfitAndLossRow("cogs", "COGS", AccountType.COST_OF_GOODS_SOLD, BigDecimal("20000")),
                ProfitAndLossRow("rent", "Rent", AccountType.EXPENSE, BigDecimal("30000")),
            ),
            pnl.expenseRows,
        )
        assertEquals(BigDecimal("95000"), pnl.totalIncome)
        assertEquals(BigDecimal("50000"), pnl.totalExpenses)
        assertEquals(BigDecimal("45000"), pnl.netProfit)
    }

    @Test
    fun entriesOutsideRangeAreExcluded() {
        val pnl = profitAndLoss(
            entries = listOf(
                saleOn("2026-09-30"),
                saleOn("2026-10-15"),
                saleOn("2026-11-01"),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(BigDecimal("100000"), pnl.totalIncome)
        assertTrue(pnl.incomeRows.size == 1)
    }

    @Test
    fun boundaryDatesAreInclusive() {
        val pnl = profitAndLoss(
            entries = listOf(
                saleOn("2026-10-01"),
                saleOn("2026-10-31"),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(BigDecimal("200000"), pnl.totalIncome)
    }

    @Test
    fun unknownAccountLinesAreExcluded() {
        val pnl = profitAndLoss(
            entries = listOf(
                entry(
                    "je-ghost",
                    "2026-10-15",
                    listOf(JournalLineRecord("ghost", BigDecimal("99999"), BigDecimal.ZERO)),
                ),
            ),
            accounts = accounts,
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertTrue(pnl.incomeRows.isEmpty())
        assertTrue(pnl.expenseRows.isEmpty())
        assertEquals(BigDecimal.ZERO, pnl.totalIncome)
        assertEquals(BigDecimal.ZERO, pnl.totalExpenses)
    }

    @Test
    fun invertedRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            profitAndLoss(
                entries = emptyList(),
                accounts = accounts,
                fromDate = "2026-10-31",
                toDate = "2026-10-01",
            )
        }
    }
}
