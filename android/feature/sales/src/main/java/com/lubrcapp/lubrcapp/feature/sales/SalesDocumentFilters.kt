package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.sales.SalesOrderStatus

/**
 * Phase 31.4 — pure status filter for the sales list. Status is
 * stored as text on the document, so the filter matches the
 * typed lifecycle names rather than re-deriving anything.
 * Narrow, order-scoped reading (the walkthrough asked about
 * pending ORDERS): PENDING and FULFILLED match sales orders
 * only — quotations and invoices are not pending orders, and
 * an invoice being PAID is not an order being fulfilled.
 * CANCELLED matches any document type; ALL is the previous
 * behaviour (everything, unfiltered).
 */
enum class SalesDocumentFilter { ALL, PENDING, FULFILLED, CANCELLED }

object SalesDocumentFilters {

    fun filterDocuments(
        documents: List<SalesDocument>,
        filter: SalesDocumentFilter,
    ): List<SalesDocument> = when (filter) {
        SalesDocumentFilter.ALL -> documents
        SalesDocumentFilter.PENDING -> documents.filter { document ->
            document.type == SalesDocumentType.SALES_ORDER &&
                document.status in setOf(
                    SalesOrderStatus.DRAFT.name,
                    SalesOrderStatus.CONFIRMED.name,
                    SalesOrderStatus.PARTIALLY_FULFILLED.name,
                )
        }
        SalesDocumentFilter.FULFILLED -> documents.filter { document ->
            document.type == SalesDocumentType.SALES_ORDER &&
                document.status == SalesOrderStatus.FULFILLED.name
        }
        SalesDocumentFilter.CANCELLED -> documents.filter { document ->
            document.status == "CANCELLED"
        }
    }
}
