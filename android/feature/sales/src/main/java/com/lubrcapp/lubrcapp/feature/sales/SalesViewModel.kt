package com.lubrcapp.lubrcapp.feature.sales

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.business.DocumentNumbering
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Offline-first sales feature: observes Room and queues writes without network access. */
@HiltViewModel
class SalesViewModel @Inject constructor(
    private val repository: OperationalBusinessRepository,
) : ViewModel() {
    fun observe(scope: OperationalScope): Flow<List<OperationalRecord>> =
        repository.observe(scope, "orders")

    suspend fun load(scope: OperationalScope): List<OperationalRecord> =
        repository.list(scope, "orders")

    suspend fun save(scope: OperationalScope, id: String, payloadJson: String, revision: Long = 0) =
        repository.save(scope, "orders", id, payloadJson, revision)

    // Phase 18.1 typed document surface. The raw record methods
    // above stay as the low-level passthrough (pinned by
    // SalesViewModelTest); everything document-shaped goes through
    // these. There is deliberately NO document delete here:
    // documents are financial records, and the repository contract
    // reserves soft-delete for nonfinancial records — a document
    // is cancelled by status (18.2), never tombstoned.

    fun observeDocuments(scope: OperationalScope): Flow<List<SalesDocument>> =
        repository.observe(scope, "orders").map { records ->
            records.map { SalesDocumentJson.decode(it.id, it.json) }
        }

    suspend fun loadDocuments(scope: OperationalScope): List<SalesDocument> =
        repository.list(scope, "orders").map { SalesDocumentJson.decode(it.id, it.json) }

    // Phase 21.1 invoice surface: invoices are "orders" records
    // with type INVOICE (same whitelisted table), filtered here
    // so callers never see quotations or orders mixed in.

    fun observeInvoices(scope: OperationalScope): Flow<List<SalesDocument>> =
        observeDocuments(scope).map { documents ->
            documents.filter { it.type == SalesDocumentType.INVOICE }
        }

    suspend fun loadInvoices(scope: OperationalScope): List<SalesDocument> =
        loadDocuments(scope).filter { it.type == SalesDocumentType.INVOICE }

    suspend fun saveDocument(scope: OperationalScope, document: SalesDocument, revision: Long = 0) =
        repository.save(scope, "orders", document.id, SalesDocumentJson.encode(document), revision)

    fun observeLines(scope: OperationalScope, orderId: String): Flow<List<SalesDocumentLine>> =
        repository.observe(scope, "order_items").map { records ->
            records.map { SalesDocumentLineJson.decode(it.id, it.json) }
                .filter { it.orderId == orderId }
                .sortedBy { it.position }
        }

    suspend fun loadLines(scope: OperationalScope, orderId: String): List<SalesDocumentLine> =
        repository.list(scope, "order_items")
            .map { SalesDocumentLineJson.decode(it.id, it.json) }
            .filter { it.orderId == orderId }
            .sortedBy { it.position }

    suspend fun saveLine(scope: OperationalScope, line: SalesDocumentLine, revision: Long = 0) =
        repository.save(scope, "order_items", line.id, SalesDocumentLineJson.encode(line), revision)

    // Phase 18.3 numbering surface. The stored record wins; until
    // one exists the §51 defaults apply. Issuing a number advances
    // and persists the counter in the same call, and a document
    // that already has a number keeps it — numbers are assigned
    // once, on first save.

    suspend fun loadNumberingRecord(scope: OperationalScope, type: SalesDocumentType): NumberingRecord {
        val stored = repository.list(scope, "numbering_sequences")
            .firstOrNull { it.id == type.name }
        return stored?.let { NumberingRecordJson.decode(it.id, it.json) }
            ?: DocumentNumberDefaults.recordFor(type)
    }

    suspend fun saveNumberingRecord(scope: OperationalScope, record: NumberingRecord, revision: Long = 0) =
        repository.save(scope, "numbering_sequences", record.id, NumberingRecordJson.encode(record), revision)

    suspend fun issueDocumentNumber(
        scope: OperationalScope,
        document: SalesDocument,
        date: LocalDate,
        branchPrefix: String? = null,
    ): SalesDocument {
        if (document.number.isNotBlank()) return document
        val record = loadNumberingRecord(scope, document.type)
        val (advancedState, formatted) = DocumentNumbering.issue(
            record.sequence(),
            record.state(),
            date,
            branchPrefix,
        )
        saveNumberingRecord(scope, record.withState(advancedState))
        return document.copy(number = formatted)
    }

    // Phase 18.4 timeline + reference surface. The timeline is
    // written by the editor's save/convert flows; the reference
    // lists feed its party and item pickers.

    suspend fun saveTimelineEntry(scope: OperationalScope, entry: TimelineEntry, revision: Long = 0) =
        repository.save(scope, "timeline_entries", entry.id, TimelineEntryJson.encode(entry), revision)

    suspend fun loadTimeline(scope: OperationalScope, documentId: String): List<TimelineEntry> =
        repository.list(scope, "timeline_entries")
            .map { TimelineEntryJson.decode(it.id, it.json) }
            .filter { it.documentId == documentId }
            .sortedBy { it.timestamp }

    fun observeParties(scope: OperationalScope): Flow<List<PartyOption>> =
        repository.observe(scope, "customers").map { SalesReferenceData.partyOptions(it) }

    fun observeItems(scope: OperationalScope): Flow<List<ItemOption>> =
        repository.observe(scope, "products").map { SalesReferenceData.itemOptions(it) }

    /**
     * Save a document and its full line set together: lines
     * that existed before but are absent from [lines] are
     * removed through the store's delete path, so an edited
     * document's totals can never be haunted by orphan lines.
     * (The document HEADER is never deleted — see the 18.1
     * note; its lines are editable components of a draft.)
     */
    suspend fun saveDocumentWithLines(
        scope: OperationalScope,
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
    ) {
        val keep = lines.map { it.id }.toSet()
        loadLines(scope, document.id)
            .filter { it.id !in keep }
            .forEach { repository.delete(scope, "order_items", it.id) }
        saveDocument(scope, document)
        lines.forEach { saveLine(scope, it) }
    }

    /**
     * The 18.4 form save, as a ViewModel operation (not a composable-local
     * function): numbers the document if blank, persists header + lines,
     * and appends the SAVED timeline entry. Returns the saved document.
     * UI state (form clearing, error display) stays with the caller.
     */
    suspend fun saveForm(
        scope: OperationalScope,
        form: SalesDocumentFormState,
        currency: CurrencyCode,
    ): SalesDocument {
        val id = form.existingId ?: UUID.randomUUID().toString()
        var document = form.toDocument(id)
        if (document.number.isBlank()) {
            val date = runCatching { LocalDate.parse(document.date) }
                .getOrDefault(LocalDate.now())
            document = issueDocumentNumber(scope, document, date)
        }
        val lines = form.toLines(id)
        saveDocumentWithLines(scope, document, lines)
        val total = SalesDocumentEngine.totals(lines, currency).grandTotal
        saveTimelineEntry(
            scope,
            TimelineEntry(
                id = UUID.randomUUID().toString(),
                documentId = document.id,
                documentType = document.type.name,
                documentNumber = document.number,
                partyId = document.partyId,
                action = "SAVED",
                grandTotal = total.amount.toPlainString(),
                timestamp = Instant.now().toString(),
            ),
        )
        return document
    }

    /**
     * The 18.5 conversion, orchestrated: load the quotation and
     * its lines, convert with fresh ids, persist the order (with
     * its lines) and the converted quotation, and return the
     * new order.
     */
    suspend fun convertQuotationToOrder(scope: OperationalScope, quotationId: String): SalesDocument {
        val quotation = loadDocuments(scope).firstOrNull { it.id == quotationId }
            ?: throw NoSuchElementException("No document $quotationId")
        val lines = loadLines(scope, quotationId)
        val result = SalesDocumentConversion.quotationToSalesOrder(
            quotation,
            lines,
            newDocumentId = java.util.UUID.randomUUID().toString(),
            newLineId = { java.util.UUID.randomUUID().toString() },
        )
        saveDocumentWithLines(scope, result.order, result.orderLines)
        saveDocument(scope, result.convertedQuotation)
        return result.order
    }

    /**
     * The 21.2 conversion, orchestrated: load the order and its
     * lines, convert with fresh ids, persist the invoice (with
     * its lines) and the converted order, and return the invoice.
     */
    suspend fun convertOrderToInvoice(scope: OperationalScope, orderId: String): SalesDocument {
        val order = loadDocuments(scope).firstOrNull { it.id == orderId }
            ?: throw NoSuchElementException("No document $orderId")
        val lines = loadLines(scope, orderId)
        val result = SalesDocumentConversion.salesOrderToInvoice(
            order,
            lines,
            newDocumentId = java.util.UUID.randomUUID().toString(),
            newLineId = { java.util.UUID.randomUUID().toString() },
        )
        saveDocumentWithLines(scope, result.invoice, result.invoiceLines)
        saveDocument(scope, result.convertedOrder)
        return result.invoice
    }

    /**
     * The 21.2 delivery, orchestrated: load the order and its
     * lines, record the delivery, persist the updated lines and
     * document, and return the updated document.
     */
    suspend fun recordDelivery(
        scope: OperationalScope,
        orderId: String,
        lineId: String,
        quantity: BigDecimal,
    ): SalesDocument {
        val order = loadDocuments(scope).firstOrNull { it.id == orderId }
            ?: throw NoSuchElementException("No document $orderId")
        val lines = loadLines(scope, orderId)
        val result = SalesDocumentStatus.recordDelivery(order, lines, lineId, quantity)
        saveDocumentWithLines(scope, result.document, result.lines)
        return result.document
    }

    // Phase 21.2 §213 reservations surface. Reservations are
    // released by status, never deleted (the store refuses
    // delete on "reservations").

    fun observeReservations(scope: OperationalScope): Flow<List<StockReservationRecord>> =
        repository.observe(scope, "reservations").map { records ->
            records.map { StockReservationJson.decode(it.id, it.json) }
        }

    suspend fun loadReservations(scope: OperationalScope): List<StockReservationRecord> =
        repository.list(scope, "reservations").map { StockReservationJson.decode(it.id, it.json) }

    suspend fun loadReservationsForOrder(scope: OperationalScope, orderId: String): List<StockReservationRecord> =
        loadReservations(scope).filter { it.orderId == orderId }

    suspend fun saveReservation(scope: OperationalScope, reservation: StockReservationRecord, revision: Long = 0) =
        repository.save(scope, "reservations", reservation.id, StockReservationJson.encode(reservation), revision)

    /**
     * Reserve an order's lines under [policy]: NEVER reserves
     * nothing; otherwise one ACTIVE reservation per line that
     * names an item (service lines carry no stock and are
     * skipped). Lines already actively reserved are left alone
     * — this call never double-reserves. Returns the
     * reservations now active for the order. The caller chooses
     * the moment (on save for ON_SUBMIT, on confirm for
     * ON_CONFIRM).
     */
    suspend fun reserveForOrder(
        scope: OperationalScope,
        orderId: String,
        policy: ReservationPolicy,
        now: String,
    ): List<StockReservationRecord> {
        if (policy == ReservationPolicy.NEVER) {
            return loadReservationsForOrder(scope, orderId)
                .filter { it.status == ReservationStatus.ACTIVE }
        }
        val lines = loadLines(scope, orderId)
        val reservedLineIds = loadReservationsForOrder(scope, orderId)
            .filter { it.status == ReservationStatus.ACTIVE }
            .map { it.lineId }.toSet()
        val fresh = lines.mapNotNull { line ->
            val itemId = line.itemId ?: return@mapNotNull null
            if (line.id in reservedLineIds) return@mapNotNull null
            StockReservationRecord(
                id = java.util.UUID.randomUUID().toString(),
                orderId = orderId,
                lineId = line.id,
                itemId = itemId,
                warehouseId = line.warehouseId,
                batchId = line.batchId,
                quantity = line.quantity,
                status = ReservationStatus.ACTIVE,
                createdAt = now,
            )
        }
        fresh.forEach { saveReservation(scope, it) }
        return loadReservationsForOrder(scope, orderId)
            .filter { it.status == ReservationStatus.ACTIVE }
    }

    /** Release every ACTIVE reservation for an order (cancel / fulfilment-complete). */
    suspend fun releaseForOrder(scope: OperationalScope, orderId: String): List<StockReservationRecord> {
        val active = loadReservationsForOrder(scope, orderId)
            .filter { it.status == ReservationStatus.ACTIVE }
        active.forEach { saveReservation(scope, it.copy(status = ReservationStatus.RELEASED)) }
        return loadReservationsForOrder(scope, orderId)
    }
}
