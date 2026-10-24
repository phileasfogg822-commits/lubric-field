package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.inventory.SerialStatus
import com.lubrcapp.lubrcapp.feature.sales.InvoicePostingAccounts
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesPostResult
import com.lubrcapp.lubrcapp.feature.sales.SalesTaxComponent
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesPostingHandlerTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")
    private val pkr = CurrencyCode.of("PKR")
    private val accounts = InvoicePostingAccounts(
        receivableAccountId = "acc-recv",
        revenueAccountId = "acc-rev",
        taxAccountId = "acc-tax",
    )

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
        val inventory = InventoryViewModel(fake)
    }

    private fun invoice() = SalesDocument(
        id = "inv-1",
        type = SalesDocumentType.INVOICE,
        number = "INV-2026-00001",
        partyId = "party-1",
        date = "2026-10-24",
        status = "SENT",
    )

    private fun invoiceLines() = listOf(
        SalesDocumentLine(
            id = "l-1", orderId = "inv-1", position = 0, itemId = "item-1",
            description = "Engine Oil 4L", quantity = BigDecimal("2"), rate = BigDecimal("5000"),
            taxComponents = listOf(SalesTaxComponent("Sales Tax", BigDecimal("17"))),
            serialNumbers = listOf("SN1", "SN2"),
        ),
    )

    @Test
    fun postingAnInvoiceWritesJournalMovementsAndFlipsSerials() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(scope, invoice(), invoiceLines())
        h.inventory.addSerial(scope, "item-1", "SN1")
        h.inventory.addSerial(scope, "item-1", "SN2")

        val result = SalesPostingHandler.postDocument(
            scope, invoice(), accounts, pkr, h.sales, h.accountsVm, h.inventory,
        )
        val posted = result as SalesPostResult.Posted
        assertEquals("INV-2026-00001", posted.documentNumber)
        assertEquals(listOf("SN1", "SN2"), posted.flippedSerials.sorted())

        val entries = h.accountsVm.loadJournalEntries(scope)
        assertEquals(1, entries.size)
        assertEquals("je-inv-1", entries[0].id)

        val movements = h.inventory.loadMovements(scope)
        assertEquals(1, movements.size)
        assertEquals("inv-1", movements[0].referenceId)
        assertEquals(BigDecimal("-2"), movements[0].quantity)

        val serials = h.inventory.loadSerialsForItem(scope, "item-1")
        assertTrue(serials.all { it.status == SerialStatus.SOLD })
    }

    @Test
    fun repostingSkipsMovementsAndKeepsOneJournalEntry() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(scope, invoice(), invoiceLines())
        h.inventory.addSerial(scope, "item-1", "SN1")
        h.inventory.addSerial(scope, "item-1", "SN2")

        SalesPostingHandler.postDocument(scope, invoice(), accounts, pkr, h.sales, h.accountsVm, h.inventory)
        val second = SalesPostingHandler.postDocument(scope, invoice(), accounts, pkr, h.sales, h.accountsVm, h.inventory)
        assertTrue(second is SalesPostResult.Posted)
        assertEquals(1, h.accountsVm.loadJournalEntries(scope).size)
        assertEquals(1, h.inventory.loadMovements(scope).size)
        assertEquals(emptyList<String>(), (second as SalesPostResult.Posted).flippedSerials)
    }

    @Test
    fun rejectedPostingWritesNothingAndFlipsNothing() = runBlocking {
        val h = Harness(FakeRepository())
        val badDate = invoice().copy(date = "not-a-date")
        h.sales.saveDocumentWithLines(scope, badDate, invoiceLines())
        h.inventory.addSerial(scope, "item-1", "SN1")

        val result = SalesPostingHandler.postDocument(
            scope, badDate, accounts, pkr, h.sales, h.accountsVm, h.inventory,
        )
        assertTrue(result is SalesPostResult.Rejected)
        assertTrue(h.accountsVm.loadJournalEntries(scope).isEmpty())
        assertTrue(h.inventory.loadMovements(scope).isEmpty())
        assertEquals(SerialStatus.IN_STOCK, h.inventory.loadSerialsForItem(scope, "item-1").first().status)
    }

    @Test
    fun postingAReturnRestoresStockAndFlipsSerialsBack() = runBlocking {
        val h = Harness(FakeRepository())
        val returnDocument = SalesDocument(
            id = "ret-1",
            type = SalesDocumentType.SALES_RETURN,
            number = "SR-2026-00001",
            partyId = "party-1",
            date = "2026-10-24",
            status = "SENT",
            convertedFromId = "inv-1",
        )
        val lines = listOf(
            SalesDocumentLine(
                id = "l-1", orderId = "ret-1", position = 0, itemId = "item-1",
                quantity = BigDecimal("1"), rate = BigDecimal("5000"),
                serialNumbers = listOf("SN1"),
            ),
        )
        h.sales.saveDocumentWithLines(scope, returnDocument, lines)
        h.inventory.addSerial(scope, "item-1", "SN1")
        h.inventory.markSerialsSold(scope, "item-1", listOf("SN1"))

        val result = SalesPostingHandler.postDocument(
            scope, returnDocument, accounts, pkr, h.sales, h.accountsVm, h.inventory,
        )
        assertTrue(result is SalesPostResult.Posted)
        assertEquals(listOf("SN1"), (result as SalesPostResult.Posted).flippedSerials)
        assertEquals(BigDecimal("1"), h.inventory.loadMovements(scope).single().quantity)
        assertEquals(SerialStatus.IN_STOCK, h.inventory.loadSerialsForItem(scope, "item-1").first().status)
    }
}
