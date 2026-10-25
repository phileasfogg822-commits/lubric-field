package com.lubrcapp.lubrcapp.feature.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DayBookTest {

    private fun entry(id: String, date: String): JournalEntryRecord =
        JournalEntryRecord(id, date, "Entry", "REF-1", emptyList(), "")

    @Test
    fun chronologicalOrderAndBoundaryInclusivity() {
        val book = dayBook(
            entries = listOf(
                entry("je-3", "2026-10-20"),
                entry("je-1", "2026-10-01"),
                entry("je-2", "2026-10-05"),
                entry("je-31", "2026-10-31"),
            ),
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(
            listOf("je-1", "je-2", "je-3", "je-31"),
            book.entries.map { it.id },
        )
    }

    @Test
    fun outOfRangeEntriesAreExcluded() {
        val book = dayBook(
            entries = listOf(
                entry("je-before", "2026-09-30"),
                entry("je-in", "2026-10-15"),
                entry("je-after", "2026-11-01"),
            ),
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(listOf("je-in"), book.entries.map { it.id })
    }

    @Test
    fun sameDateEntriesSortById() {
        val book = dayBook(
            entries = listOf(
                entry("je-b", "2026-10-10"),
                entry("je-a", "2026-10-10"),
            ),
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertEquals(listOf("je-a", "je-b"), book.entries.map { it.id })
    }

    @Test
    fun invertedRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            dayBook(
                entries = emptyList(),
                fromDate = "2026-10-31",
                toDate = "2026-10-01",
            )
        }
    }

    @Test
    fun emptyLedgerProducesEmptyBook() {
        val book = dayBook(
            entries = emptyList(),
            fromDate = "2026-10-01",
            toDate = "2026-10-31",
        )
        assertTrue(book.entries.isEmpty())
    }
}
