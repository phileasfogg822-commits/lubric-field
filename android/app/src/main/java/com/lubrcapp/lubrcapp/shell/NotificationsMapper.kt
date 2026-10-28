package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.business.Notification
import com.lubrcapp.lubrcapp.domain.business.NotificationType
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
 *
 * Phase 31.5 — the detail now carries days
 * overdue plus a band: 1–7 days "recently
 * overdue", 8–30 days "overdue", 31+ days "long
 * overdue" (e.g. "Invoice INV-1 overdue by 12
 * days — overdue"). When either date fails to
 * parse, the row keeps the 29.5 detail instead
 * of disappearing: a malformed date is a data
 * problem, not a reason to hide an overdue
 * invoice, so 31.5 never shows fewer alerts
 * than 29.5 did. The id, type, title and date
 * fields are untouched, so the center,
 * mark-read, and existing tests keep working.
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
                (document.dueDate?.let { it < today } == true)
        }.map { document ->
            val number = document.number.ifBlank { document.id }
            Notification(
                id = "overdue-${document.id}",
                type = NotificationType.PAYMENT_OVERDUE,
                title = document.partyId?.let { partyNames[it] } ?: number,
                detail = overdueDetail(number, document.dueDate, today),
                date = today,
            )
        }

    private fun overdueDetail(number: String, dueDate: String?, today: String): String {
        val days = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(dueDate), LocalDate.parse(today))
        }.getOrNull() ?: return "Invoice $number overdue"
        val band = when {
            days <= 7 -> "recently overdue"
            days <= 30 -> "overdue"
            else -> "long overdue"
        }
        return "Invoice $number overdue by $days days — $band"
    }
}
