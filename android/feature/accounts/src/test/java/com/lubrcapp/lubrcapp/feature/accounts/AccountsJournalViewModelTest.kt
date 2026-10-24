package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsJournalViewModelTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeJournalRepository : OperationalBusinessRepository {
        val rows = mutableListOf<OperationalRecord>()
        val tables = mutableListOf<String>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
            tables.add(table)
            return flowOf(rows.toList())
        }

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
            tables.add(table)
            return rows.toList()
        }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            tables.add(table)
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    @Test
    fun saveAndLoadJournalEntryRoundTrips() = runBlocking {
        val fake = FakeJournalRepository()
        val vm = AccountsViewModel(fake)
        val entry = JournalEntryRecord(
            id = "je-1",
            date = "2026-10-24",
            description = "Credit sale",
            referenceNumber = "INV-2026-00001",
            lines = listOf(
                JournalLineRecord("receivable", BigDecimal("100000"), BigDecimal.ZERO),
                JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
            ),
            createdAt = "2026-10-24T00:00:00Z",
        )
        vm.saveJournalEntry(scope, entry)
        val loaded = vm.loadJournalEntries(scope)
        assertEquals(listOf(entry), loaded)
        assertTrue(fake.tables.all { it == "journal_entries" })
    }

    @Test
    fun postJournalEntryStampsFreshIdAndTimestamp() = runBlocking {
        val fake = FakeJournalRepository()
        val vm = AccountsViewModel(fake)
        val posted = vm.postJournalEntry(
            scope = scope,
            date = "2026-10-24",
            description = "Cash receipt",
            lines = listOf(JournalLineRecord("cash", BigDecimal("500"), BigDecimal.ZERO)),
        )
        assertTrue(posted.id.isNotBlank())
        assertTrue(posted.createdAt.isNotBlank())
        assertEquals(1, vm.loadJournalEntries(scope).size)
    }

    @Test
    fun loadJournalEntriesSortsByDateThenCreatedAtThenId() = runBlocking {
        val fake = FakeJournalRepository()
        val vm = AccountsViewModel(fake)
        val late = JournalEntryRecord("je-late", "2026-10-25", "Late", null, emptyList(), "2026-10-25T00:00:00Z")
        val early = JournalEntryRecord("je-early", "2026-10-23", "Early", null, emptyList(), "2026-10-23T00:00:00Z")
        val middle = JournalEntryRecord("je-mid", "2026-10-24", "Mid", null, emptyList(), "2026-10-24T00:00:00Z")
        vm.saveJournalEntry(scope, late)
        vm.saveJournalEntry(scope, early)
        vm.saveJournalEntry(scope, middle)
        assertEquals(listOf(early, middle, late), vm.loadJournalEntries(scope))
    }
}
