package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesViewModelDocumentsTest {

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableMap<String, String>>()
        val calls = mutableListOf<String>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> {
            calls.add("observe:$table")
            return flowOf(records(table))
        }

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> {
            calls.add("list:$table")
            return records(table)
        }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            calls.add("save:$table:$id")
            tables.getOrPut(table) { mutableMapOf() }[id] = json
        }

        private fun records(table: String): List<OperationalRecord> =
            tables[table].orEmpty().map { (id, json) -> OperationalRecord(id, json) }
    }

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    @Test
    fun documentsSaveToOrdersAndLoadBackTyped() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val document = SalesDocument(
            id = "doc-1",
            type = SalesDocumentType.QUOTATION,
            number = "QT-2026-00001",
            partyId = "party-1",
            date = "2026-10-23",
        )
        vm.saveDocument(scope, document)
        assertEquals(listOf(document), vm.loadDocuments(scope))
        assertEquals(listOf("save:orders:doc-1", "list:orders"), fake.calls)
    }

    @Test
    fun invoicesLoadFilteredToInvoiceTypeOnly() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val quotation = SalesDocument(id = "doc-q", type = SalesDocumentType.QUOTATION, date = "2026-10-24")
        val order = SalesDocument(id = "doc-o", type = SalesDocumentType.SALES_ORDER, date = "2026-10-24")
        val invoice = SalesDocument(
            id = "inv-1",
            type = SalesDocumentType.INVOICE,
            number = "INV-2026-00001",
            date = "2026-10-24",
        )
        vm.saveDocument(scope, quotation)
        vm.saveDocument(scope, order)
        vm.saveDocument(scope, invoice)
        assertEquals(listOf(invoice), vm.loadInvoices(scope))
    }

    @Test
    fun linesSaveToOrderItemsAndLoadFilteredAndSortedByPosition() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val second = SalesDocumentLine(
            id = "line-2", orderId = "doc-1", position = 1,
            quantity = BigDecimal("1"), rate = BigDecimal("200"),
        )
        val first = SalesDocumentLine(
            id = "line-1", orderId = "doc-1", position = 0,
            quantity = BigDecimal("2"), rate = BigDecimal("500"),
        )
        val otherDocumentLine = SalesDocumentLine(
            id = "line-9", orderId = "doc-2", position = 0,
            quantity = BigDecimal("1"), rate = BigDecimal("1"),
        )
        vm.saveLine(scope, second)
        vm.saveLine(scope, first)
        vm.saveLine(scope, otherDocumentLine)

        assertEquals(listOf(first, second), vm.loadLines(scope, "doc-1"))
        assertEquals(listOf("save:order_items:line-2", "save:order_items:line-1", "save:order_items:line-9", "list:order_items"), fake.calls)
    }
}
