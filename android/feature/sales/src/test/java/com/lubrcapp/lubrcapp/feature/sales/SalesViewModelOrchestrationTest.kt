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
    fun orderToInvoiceConversionPersistsInvoiceAndMarksTheOrder() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val order = SalesDocument(
            id = "o-1",
            type = SalesDocumentType.SALES_ORDER,
            number = "SO-2026-00003",
            status = "CONFIRMED",
        )
        vm.saveDocumentWithLines(scope, order, listOf(line("l-1", "o-1", 0)))

        val invoice = vm.convertOrderToInvoice(scope, "o-1")
        assertEquals(SalesDocumentType.INVOICE, invoice.type)
        assertEquals("o-1", invoice.convertedFromId)
        assertEquals(1, vm.loadLines(scope, invoice.id).size)

        val storedOrder = vm.loadDocuments(scope).first { it.id == "o-1" }
        assertEquals(invoice.id, storedOrder.convertedToId)
        assertEquals("CONFIRMED", storedOrder.status)
    }

    @Test
    fun invoiceToReturnConversionPersistsReturnAndLeavesTheInvoiceUnlinked() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val invoice = SalesDocument(
            id = "inv-1",
            type = SalesDocumentType.INVOICE,
            number = "INV-2026-00001",
            status = "SENT",
        )
        vm.saveDocumentWithLines(scope, invoice, listOf(line("l-1", "inv-1", 0)))

        val returnDocument = vm.convertInvoiceToReturn(scope, "inv-1")
        assertEquals(SalesDocumentType.SALES_RETURN, returnDocument.type)
        assertEquals("inv-1", returnDocument.convertedFromId)
        assertEquals(1, vm.loadLines(scope, returnDocument.id).size)

        // No forward link: the invoice takes many partial returns.
        val storedInvoice = vm.loadDocuments(scope).first { it.id == "inv-1" }
        assertNull(storedInvoice.convertedToId)
        assertEquals("SENT", storedInvoice.status)
    }

    @Test
    fun deliveryPersistsUpdatedLinesAndDerivedStatus() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val order = SalesDocument(id = "o-1", type = SalesDocumentType.SALES_ORDER, status = "CONFIRMED")
        vm.saveDocumentWithLines(scope, order, listOf(line("l-1", "o-1", 0)))

        val updated = vm.recordDelivery(scope, "o-1", "l-1", BigDecimal("1"))
        assertEquals("FULFILLED", updated.status)
        assertEquals(BigDecimal("1"), vm.loadLines(scope, "o-1").first().fulfilledQuantity)
    }

    @Test
    fun reservationsReserveReleaseAndNeverDoubleReserve() = runBlocking {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        val order = SalesDocument(id = "o-1", type = SalesDocumentType.SALES_ORDER, status = "CONFIRMED")
        val stocked = line("l-1", "o-1", 0).copy(itemId = "item-1")
        val service = line("l-2", "o-1", 1)
        vm.saveDocumentWithLines(scope, order, listOf(stocked, service))

        val reserved = vm.reserveForOrder(scope, "o-1", ReservationPolicy.ON_CONFIRM, "2026-10-24T10:00:00Z")
        assertEquals(1, reserved.size)
        assertEquals("item-1", reserved[0].itemId)
        assertEquals(BigDecimal("1"), reserved[0].quantity)

        val again = vm.reserveForOrder(scope, "o-1", ReservationPolicy.ON_CONFIRM, "2026-10-24T10:00:00Z")
        assertEquals(1, again.size)

        val never = vm.reserveForOrder(scope, "o-1", ReservationPolicy.NEVER, "2026-10-24T10:00:00Z")
        assertEquals(1, never.size)

        val released = vm.releaseForOrder(scope, "o-1")
        assertEquals(1, released.size)
        assertEquals(ReservationStatus.RELEASED, released[0].status)
        assertEquals(0, vm.loadReservationsForOrder(scope, "o-1").filter { it.status == ReservationStatus.ACTIVE }.size)
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
