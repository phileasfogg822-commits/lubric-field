package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.JournalEntryRecord
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLedgerBackfillTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val pkr = CurrencyCode.of("PKR")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableList<OperationalRecord>>()
        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(tables[table]?.toList() ?: emptyList())
        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            tables[table]?.toList() ?: emptyList()
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            val rows = tables.getOrPut(table) { mutableListOf() }
            rows.removeAll { it.id == id }
            rows.add(OperationalRecord(id, json))
        }
    }

    private class Harness(fake: FakeRepository) {
        val sales = SalesViewModel(fake)
        val accountsVm = AccountsViewModel(fake)
    }

    private fun document(id: String, type: SalesDocumentType, number: String, partyId: String?) =
        SalesDocument(
            id = id,
            type = type,
            number = number,
            partyId = partyId,
            date = "2026-10-24",
            status = "SENT",
        )

    private fun lines(orderId: String, quantity: String, rate: String) = listOf(
        SalesDocumentLine(
            id = "l-1", orderId = orderId, position = 0, itemId = "item-1",
            description = "Engine Oil 4L", quantity = BigDecimal(quantity), rate = BigDecimal(rate),
            taxComponents = emptyList(),
        ),
    )

    private fun journalEntry(id: String, number: String) = JournalEntryRecord(
        id = id,
        date = "2026-10-24",
        description = "INVOICE $number",
        referenceNumber = number,
        createdAt = "2026-10-24T00:00:00Z",
    )

    @Test
    fun backfillWritesInvoiceRowFromJournalEntry() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(scope, document("inv-1", SalesDocumentType.INVOICE, "INV-2026-00001", "party-1"), lines("inv-1", "2", "5000"))
        h.accountsVm.saveJournalEntry(scope, journalEntry("je-inv-1", "INV-2026-00001"))

        val result = PartyLedgerBackfill.backfill(scope, pkr, h.sales, h.accountsVm)

        assertEquals(1, result.written)
        assertEquals(0, result.skipped)
        val rows = h.accountsVm.loadLedgerRowsForParty(scope, "party-1")
        assertEquals(1, rows.size)
        assertEquals("je-inv-1", rows[0].id)
        assertEquals("inv-1", rows[0].transactionId)
        assertEquals("INVOICE", rows[0].documentType)
        assertEquals(0, BigDecimal("10000").compareTo(rows[0].amount))
    }

    @Test
    fun backfillWritesNegativeRowForReturn() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(scope, document("ret-1", SalesDocumentType.SALES_RETURN, "SR-2026-00001", "party-1"), lines("ret-1", "1", "3000"))
        h.accountsVm.saveJournalEntry(scope, journalEntry("je-ret-1", "SR-2026-00001"))

        val result = PartyLedgerBackfill.backfill(scope, pkr, h.sales, h.accountsVm)

        assertEquals(1, result.written)
        val rows = h.accountsVm.loadLedgerRowsForParty(scope, "party-1")
        assertEquals(1, rows.size)
        assertEquals(0, BigDecimal("-3000").compareTo(rows[0].amount))
    }

    @Test
    fun backfillSkipsUnmatchedAndPartylessEntriesAndRerunsCleanly() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(scope, document("inv-1", SalesDocumentType.INVOICE, "INV-2026-00001", "party-1"), lines("inv-1", "2", "5000"))
        h.sales.saveDocumentWithLines(scope, document("inv-2", SalesDocumentType.INVOICE, "INV-2026-00002", null), lines("inv-2", "1", "7000"))
        h.accountsVm.saveJournalEntry(scope, journalEntry("je-inv-1", "INV-2026-00001"))
        h.accountsVm.saveJournalEntry(scope, journalEntry("je-inv-2", "INV-2026-00002"))
        h.accountsVm.saveJournalEntry(scope, journalEntry("je-ghost-1", "INV-2026-00009"))

        val first = PartyLedgerBackfill.backfill(scope, pkr, h.sales, h.accountsVm)
        assertEquals(1, first.written)
        assertEquals(2, first.skipped)
        assertEquals(1, h.accountsVm.loadLedgerRowsForParty(scope, "party-1").size)

        // A second run overwrites the same row id: still one row, no duplicates.
        val second = PartyLedgerBackfill.backfill(scope, pkr, h.sales, h.accountsVm)
        assertEquals(1, second.written)
        assertEquals(1, h.accountsVm.loadLedgerRowsForParty(scope, "party-1").size)
    }

    @Test
    fun backfillOnEmptyJournalWritesNothing() = runBlocking {
        val h = Harness(FakeRepository())
        val result = PartyLedgerBackfill.backfill(scope, pkr, h.sales, h.accountsVm)
        assertEquals(0, result.written)
        assertEquals(0, result.skipped)
        assertTrue(h.accountsVm.loadLedgerRowsForParty(scope, "party-1").isEmpty())
    }
}
