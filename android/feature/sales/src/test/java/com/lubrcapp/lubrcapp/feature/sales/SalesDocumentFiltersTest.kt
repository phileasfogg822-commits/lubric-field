package com.lubrcapp.lubrcapp.feature.sales

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesDocumentFiltersTest {
    private fun doc(id: String, type: SalesDocumentType, status: String) =
        SalesDocument(id = id, type = type, status = status)

    private val mixed = listOf(
        doc("o-draft", SalesDocumentType.SALES_ORDER, "DRAFT"),
        doc("o-confirmed", SalesDocumentType.SALES_ORDER, "CONFIRMED"),
        doc("o-partial", SalesDocumentType.SALES_ORDER, "PARTIALLY_FULFILLED"),
        doc("o-fulfilled", SalesDocumentType.SALES_ORDER, "FULFILLED"),
        doc("o-cancelled", SalesDocumentType.SALES_ORDER, "CANCELLED"),
        doc("q-draft", SalesDocumentType.QUOTATION, "DRAFT"),
        doc("i-sent", SalesDocumentType.INVOICE, "SENT"),
        doc("i-cancelled", SalesDocumentType.INVOICE, "CANCELLED"),
    )

    @Test fun allReturnsEverything() {
        assertEquals(
            mixed.map { it.id },
            SalesDocumentFilters.filterDocuments(mixed, SalesDocumentFilter.ALL).map { it.id },
        )
    }

    @Test fun pendingMatchesSalesOrdersOnlyInOpenStatuses() {
        assertEquals(
            listOf("o-draft", "o-confirmed", "o-partial"),
            SalesDocumentFilters.filterDocuments(mixed, SalesDocumentFilter.PENDING).map { it.id },
        )
    }

    @Test fun fulfilledMatchesFulfilledOrdersOnly() {
        assertEquals(
            listOf("o-fulfilled"),
            SalesDocumentFilters.filterDocuments(mixed, SalesDocumentFilter.FULFILLED).map { it.id },
        )
    }

    @Test fun cancelledMatchesAnyType() {
        assertEquals(
            listOf("o-cancelled", "i-cancelled"),
            SalesDocumentFilters.filterDocuments(mixed, SalesDocumentFilter.CANCELLED).map { it.id },
        )
    }
}
