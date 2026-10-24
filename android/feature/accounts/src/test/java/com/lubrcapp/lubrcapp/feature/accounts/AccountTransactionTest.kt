package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineTotals
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTransactionTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val pkr = CurrencyCode.of("PKR")

    private class TableAwareFake : OperationalBusinessRepository {
        val rows = mutableListOf<Pair<String, OperationalRecord>>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(rows.filter { it.first == table }.map { it.second })

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            rows.filter { it.first == table }.map { it.second }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            rows.removeAll { it.first == table && it.second.id == id }
            rows.add(table to OperationalRecord(id, json))
        }
    }

    private fun money(amount: String): Money = Money.of(amount, pkr)

    private fun creditSalePosting(): DocumentPosting {
        val line = DocumentLineTotals(
            grossAmount = money("100000"),
            discountAmount = money("0"),
            taxableBase = money("100000"),
            taxComponents = emptyList(),
            totalTax = money("0"),
            lineTotal = money("100000"),
        )
        return DocumentPosting(
            transactionId = TransactionId("tx-1"),
            documentType = "INVOICE",
            documentNumber = "INV-2026-00001",
            totals = DocumentTotals(
                lines = listOf(line),
                charges = emptyList(),
                grossBeforeDiscounts = money("100000"),
                lineDiscountsTotal = money("0"),
                taxableBaseTotal = money("100000"),
                taxComponents = emptyList(),
                taxTotal = money("0"),
                chargesTotal = money("0"),
                invoiceDiscountAmount = money("0"),
                roundOffAmount = money("0"),
                grandTotal = money("100000"),
            ),
            journalLines = listOf(
                PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
                PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("100000")),
            ),
        )
    }

    @Test
    fun transactionCodecRoundTrips() {
        val row = AccountTransactionRecord(
            id = "je-tx-1-0",
            entryId = "je-tx-1",
            accountId = "receivable",
            debit = BigDecimal("100000"),
            date = "2026-10-24",
            description = "INVOICE INV-2026-00001",
            referenceNumber = "INV-2026-00001",
            createdAt = "2026-10-24T00:00:00Z",
        )
        assertEquals(row, AccountTransactionJson.decode(row.id, AccountTransactionJson.encode(row)))
    }

    @Test
    fun transactionCodecToleratesGarbage() {
        val decoded = AccountTransactionJson.decode("row-9", "{broken")
        assertEquals("row-9", decoded.id)
        assertEquals(BigDecimal.ZERO, decoded.debit)
    }

    @Test
    fun fromEntryMapsOneRowPerLineWithStableIds() {
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
        val rows = AccountTransactionRecord.fromEntry(entry)
        assertEquals(listOf("je-1-0", "je-1-1"), rows.map { it.id })
        assertTrue(rows.all { it.entryId == "je-1" })
        assertEquals(BigDecimal("100000"), rows[0].debit)
        assertEquals(BigDecimal("100000"), rows[1].credit)
    }

    @Test
    fun postDocumentSavesEntryAndRows() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val result = vm.postDocument(
            scope = scope,
            posting = creditSalePosting(),
            date = LocalDate.parse("2026-10-24"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
            deviceId = DeviceId("device-a"),
        )
        assertTrue(result is AccountsViewModel.DocumentPostResult.Posted)
        val posted = result as AccountsViewModel.DocumentPostResult.Posted
        assertEquals("je-tx-1", posted.entry.id)
        assertEquals(2, posted.transactions.size)
        assertEquals(1, vm.loadJournalEntries(scope).size)
        assertEquals(2, vm.loadTransactionsForEntry(scope, "je-tx-1").size)
    }

    @Test
    fun repostOverwritesInsteadOfDuplicating() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val posting = creditSalePosting()
        val date = LocalDate.parse("2026-10-24")
        val now = Instant.parse("2026-10-24T00:00:00Z")
        vm.postDocument(scope, posting, date, now = now, deviceId = DeviceId("device-a"))
        vm.postDocument(scope, posting, date, now = now, deviceId = DeviceId("device-a"))
        assertEquals(1, vm.loadJournalEntries(scope).size)
        assertEquals(2, vm.loadTransactionsForEntry(scope, "je-tx-1").size)
    }

    @Test
    fun rejectedPostingSavesNothing() = runBlocking {
        val fake = TableAwareFake()
        val vm = AccountsViewModel(fake)
        val unbalanced = creditSalePosting().copy(
            journalLines = listOf(
                PostingJournalLine(AccountId("receivable"), money("100000"), Money.zero(pkr)),
                PostingJournalLine(AccountId("sales"), Money.zero(pkr), money("99999")),
            ),
        )
        val result = vm.postDocument(
            scope = scope,
            posting = unbalanced,
            date = LocalDate.parse("2026-10-24"),
            now = Instant.parse("2026-10-24T00:00:00Z"),
            deviceId = DeviceId("device-a"),
        )
        assertTrue(result is AccountsViewModel.DocumentPostResult.Rejected)
        assertEquals(0, vm.loadJournalEntries(scope).size)
        assertEquals(0, vm.loadTransactionsForEntry(scope, "je-tx-1").size)
    }
}
