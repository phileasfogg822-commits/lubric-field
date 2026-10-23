package com.lubrcapp.lubrcapp.feature.sales

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.business.DocumentNumbering
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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
}
