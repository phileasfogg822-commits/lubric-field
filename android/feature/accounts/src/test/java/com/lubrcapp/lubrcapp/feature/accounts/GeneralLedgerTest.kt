package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralLedgerTest {

    private val accounts = listOf(
        AccountRecord("cash", "1000", "Cash", AccountType.ASSET),
        AccountRecord("sales", "4000", "Sales", AccountType.INCOME),
    )

    private fun entry(id: String, date: String, lines: List<JournalLineRecord>): JournalEntryRecord =
        JournalEntryRecord(id, date, "Entry", "REF-1", lines, "")

    private fun line(accountId: String, debit: String, credit: String): JournalLineRecord =
        JournalLineRecord(accountId, BigDecimal(debit), BigDecimal(credit))

    @Test
    fun runningBalancesAndRangeExclusion() {
        val ledger = generalLedger(
            entries = listOf(
                entry(
                    "je-before",
                    "2026-09-30",
                    listOf(line("cash", "50000", "0")),
                ),
                entry(
                    "je-1",
                    "2026-10-05",
                    listOf(line("cash", "10000", "0")),
                ),
                entry(
                    "je-2",
                    "2026-10-12",
                    listOf(line("cash", "0", "30000")),
                ),
                entry(
                    "je-3",
                    "2026-10-20",
                    listOf(
                        line("cash", "5000", "0"),
                        line("cash", "0", "2000"),
                    ),
                ),
                entry(
                    "je-after",
                    "2026-11-01",
                    listOf(line("cash", "99999", "0")),
                ),
            ),
            accounts = accounts,
            accountId = "cash",
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals("Cash", ledger.accountName)
        assertEquals(BigDecimal("50000"), ledger.openingBalance)
        assertEquals(listOf("je-1", "je-2", "je-3"), ledger.movements.map { it.entryId })
        assertEquals(BigDecimal("60000"), ledger.movements[0].runningBalance)
        assertEquals(BigDecimal("30000"), ledger.movements[1].runningBalance)
        assertEquals(BigDecimal("33000"), ledger.movements[2].runningBalance)
        assertEquals(BigDecimal("33000"), ledger.closingBalance)
    }

    @Test
    fun multipleLinesOnSameAccountAreSummedPerEntry() {
        val ledger = generalLedger(
            entries = listOf(
                entry(
                    "je-multi",
                    "2026-10-05",
                    listOf(
                        line("cash", "1000", "0"),
                        line("cash", "2000", "0"),
                        line("cash", "0", "500"),
                    ),
                ),
            ),
            accounts = accounts,
            accountId = "cash",
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(BigDecimal.ZERO, ledger.openingBalance)
        assertTrue(ledger.movements.size == 1)
        assertEquals(BigDecimal("3000"), ledger.movements[0].debit)
        assertEquals(BigDecimal("500"), ledger.movements[0].credit)
        assertEquals(BigDecimal("2500"), ledger.movements[0].runningBalance)
        assertEquals(BigDecimal("2500"), ledger.closingBalance)
    }

    @Test
    fun unknownAccountProducesEmptyLedger() {
        val ledger = generalLedger(
            entries = listOf(
                entry(
                    "je-1",
                    "2026-10-05",
                    listOf(line("cash", "10000", "0")),
                ),
            ),
            accounts = accounts,
            accountId = "ghost",
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals("ghost", ledger.accountName)
        assertTrue(ledger.movements.isEmpty())
        assertEquals(BigDecimal.ZERO, ledger.openingBalance)
        assertEquals(BigDecimal.ZERO, ledger.closingBalance)
    }

    @Test
    fun invertedRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            generalLedger(
                entries = emptyList(),
                accounts = accounts,
                accountId = "cash",
                fromDate = "2026-10-31",
                toDate = "2026-10-01",
            )
        }
    }
}
