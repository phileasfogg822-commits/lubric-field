package com.lubrcapp.lubrcapp.feature.sales

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

class SalesViewModelOrchestrationTest {

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableMap<String, String>>()
        val deleted = mutableListOf<String>()

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            flowOf(records(table))

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            records(table)

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            tables.getOrPut(table) { mutableMapOf() }[id] = json
        }

        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deleted.add("$table:$id")
            tables[table]?.remove(id)
        }

        private fun records(table: String): List<OperationalRecord> =
            tables[table].orEmpty().map { (id, json) -> OperationalRecord(id, json) }
    }

    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private fun line(id: String, orderId: String, position: Int) = SalesDocumentLine(
        id = id,
        orderId = orderId,
        position = position,
        quantity = BigDecimal("1"),
        rate = BigDecimal("100"),
    )

    @Test
    fun savingADocumentDeletesLinesThatWereRemovedInTheEditor() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val document = SalesDocument(id = "doc-1", type = SalesDocumentType.SALES_ORDER, status = "DRAFT")
        vm.saveDocumentWithLines(scope, document, listOf(line("l-1", "doc-1", 0), line("l-2", "doc-1", 1)))
        vm.saveDocumentWithLines(scope, document, listOf(line("l-1", "doc-1", 0)))
        assertEquals(listOf("order_items:l-2"), fake.deleted)
        assertEquals(listOf("l-1"), vm.loadLines(scope, "doc-1").map { it.id })
    }

    @Test
    fun conversionPersistsTheOrderAndMarksTheQuotation() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val quotation = SalesDocument(
            id = "q-1",
            type = SalesDocumentType.QUOTATION,
            number = "QT-2026-00007",
            status = "ACCEPTED",
        )
        vm.saveDocumentWithLines(scope, quotation, listOf(line("l-1", "q-1", 0)))

        val order = vm.convertQuotationToOrder(scope, "q-1")
        assertEquals(SalesDocumentType.SALES_ORDER, order.type)
        assertEquals("q-1", order.convertedFromId)
        assertEquals(1, vm.loadLines(scope, order.id).size)
        assertTrue(vm.loadLines(scope, order.id)[0].id != "l-1")

        val storedQuotation = vm.loadDocuments(scope).first { it.id == "q-1" }
        assertEquals("CONVERTED", storedQuotation.status)
        assertEquals(order.id, storedQuotation.convertedToId)
    }

    @Test
    fun timelineEntriesSaveAndLoadPerDocument() = runBlocking {
        val vm = SalesViewModel(FakeRepository())
        vm.saveTimelineEntry(
            scope,
            TimelineEntry(
                id = "t-2", documentId = "doc-1", documentType = "QUOTATION",
                action = "SAVED", timestamp = "2026-10-23T19:00:00Z",
            ),
        )
        vm.saveTimelineEntry(
            scope,
            TimelineEntry(
                id = "t-1", documentId = "doc-1", documentType = "QUOTATION",
                action = "SAVED", timestamp = "2026-10-23T18:00:00Z",
            ),
        )
        vm.saveTimelineEntry(
            scope,
            TimelineEntry(
                id = "t-3", documentId = "doc-2", documentType = "SALES_ORDER",
                action = "SAVED", timestamp = "2026-10-23T18:30:00Z",
            ),
        )
        assertEquals(listOf("t-1", "t-2"), vm.loadTimeline(scope, "doc-1").map { it.id })
    }
}
