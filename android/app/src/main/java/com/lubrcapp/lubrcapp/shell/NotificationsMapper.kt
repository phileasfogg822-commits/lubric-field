package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.business.Notification
import com.lubrcapp.lubrcapp.domain.business.NotificationType
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType

/**
 * Phase 29.5 — notification mappings from stored
 * sales documents. Overdue means: an INVOICE
 * document, not a DRAFT (a draft is not owed),
 * with a due date before today. Stored dates
 * are ISO text, so lexicographic comparison is
 * chronological (the journal-entry convention) —
 * no calendar parsing on possibly-ragged stored
 * data. The title is the party name when the
 * shell mapped one, else the document number
 * (or id when unnumbered).
 */
object NotificationsMapper {
    fun overdueInvoices(
        documents: List<SalesDocument>,
        partyNames: Map<String, String>,
        today: String,
    ): List<Notification> =
        documents.filter { document ->
            document.type == SalesDocumentType.INVOICE &&
                document.status != "DRAFT" &&
                document.dueDate != null &&
                document.dueDate < today
        }.map { document ->
            val number = document.number.ifBlank { document.id }
            Notification(
                id = "overdue-${document.id}",
                type = NotificationType.PAYMENT_OVERDUE,
                title = document.partyId?.let { partyNames[it] } ?: number,
                detail = "Invoice $number overdue",
                date = today,
            )
        }
}
