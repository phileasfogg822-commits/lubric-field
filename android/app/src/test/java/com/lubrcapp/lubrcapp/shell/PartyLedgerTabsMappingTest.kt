package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerRecord
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PartyLedgerTabsMappingTest {

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

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

    private fun ledgerRow(id: String, partyId: String, amount: String, number: String, date: String, type: String) =
        PartyLedgerRecord(
            id = id,
            entryId = id,
            partyId = partyId,
            direction = "RECEIVABLE",
            amount = BigDecimal(amount),
            currency = "PKR",
            transactionId = id.removePrefix("je-"),
            documentType = type,
            documentNumber = number,
            date = date,
            description = "$type $number",
            createdAt = "${date}T00:00:00Z",
        )

    private fun invoiceLines(orderId: String) = listOf(
        SalesDocumentLine(
            id = "l-1", orderId = orderId, position = 0, itemId = "item-1",
            description = "Engine Oil 4L", quantity = BigDecimal("2"), rate = BigDecimal("5000"),
            taxComponents = emptyList(),
        ),
    )

    @Test
    fun tabsForMapsProfileOrdersAndItems() = runBlocking {
        val h = Harness(FakeRepository())
        h.accountsVm.savePartyLedgerRow(scope, ledgerRow("je-inv-1", "party-1", "10000", "INV-2026-00001", "2026-10-01", "INVOICE"))
        h.accountsVm.savePartyLedgerRow(scope, ledgerRow("je-pay-1", "party-1", "-4000", "PAY-2026-00001", "2026-10-05", "PAYMENT"))
        h.sales.saveDocumentWithLines(
            scope,
            SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, number = "INV-2026-00001", partyId = "party-1", date = "2026-10-01", status = "SENT"),
            invoiceLines("inv-1"),
        )

        val tabs = PartyLedgerTabsMapping.tabsFor(scope, "party-1", h.sales, h.accountsVm)

        assertEquals(2, tabs.transactions.size)
        assertEquals("10000", tabs.transactions[0].amountText)
        assertEquals("-4000", tabs.transactions[1].amountText)
        assertEquals(2, tabs.statement.size)
        assertEquals("10000", tabs.statement[0].runningBalanceText)
        assertEquals("6000", tabs.statement[1].runningBalanceText)
        assertEquals(1, tabs.payments.size)
        assertEquals("-4000", tabs.payments[0].amountText)
        assertEquals(1, tabs.orders.size)
        assertEquals("INV-2026-00001", tabs.orders[0].number)
        assertEquals("10000", tabs.orders[0].totalText)
        assertEquals(1, tabs.items.size)
        assertEquals("Engine Oil 4L", tabs.items[0].name)
        assertEquals("2", tabs.items[0].quantityText)
        assertEquals("10000", tabs.items[0].totalText)
    }

    @Test
    fun tabsForPartyWithoutRowsStillListsOrders() = runBlocking {
        val h = Harness(FakeRepository())
        h.sales.saveDocumentWithLines(
            scope,
            SalesDocument(id = "inv-9", type = SalesDocumentType.INVOICE, number = "INV-2026-00009", partyId = "party-2", date = "2026-10-02", status = "DRAFT"),
            invoiceLines("inv-9"),
        )

        val tabs = PartyLedgerTabsMapping.tabsFor(scope, "party-2", h.sales, h.accountsVm)

        assertEquals(0, tabs.transactions.size)
        assertEquals(0, tabs.statement.size)
        assertEquals(0, tabs.payments.size)
        assertEquals(1, tabs.orders.size)
        assertEquals("10000", tabs.orders[0].totalText)
        assertEquals(1, tabs.items.size)
    }
}
