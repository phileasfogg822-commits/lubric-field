package com.lubrcapp.lubrcapp.feature.accounts

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CashBookTest {
    private val opening = JournalEntryRecord(
        id = "je-1",
        date = "2026-10-24",
        description = "Opening cash",
        lines = listOf(
            JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
            JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
        ),
    )
    private val expense = JournalEntryRecord(
        id = "je-2",
        date = "2026-10-20",
        description = "Petty expense",
        lines = listOf(
            JournalLineRecord("cash", BigDecimal.ZERO, BigDecimal("20000")),
            JournalLineRecord("expense", BigDecimal("20000"), BigDecimal.ZERO),
        ),
    )
    private val unrelated = JournalEntryRecord(
        id = "je-3",
        date = "2026-10-22",
        description = "Bank transfer",
        lines = listOf(
            JournalLineRecord("bank", BigDecimal("5000"), BigDecimal.ZERO),
            JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("5000")),
        ),
    )

    @Test fun entriesForAccountFiltersAndOrdersByDate() {
        val found = CashBook.entriesForAccount(listOf(opening, expense, unrelated), "cash")
        assertEquals(listOf("je-2", "je-1"), found.map { it.id })
    }

    @Test fun balanceIsDebitMinusCreditOnThatAccountOnly() {
        val entries = listOf(opening, expense, unrelated)
        assertEquals(0, CashBook.balance(entries, "cash").compareTo(BigDecimal("80000")))
        assertEquals(0, CashBook.balance(entries, "sales").compareTo(BigDecimal("-105000")))
        assertEquals(0, CashBook.balance(entries, "missing").compareTo(BigDecimal.ZERO))
    }
}
