package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.business.NotificationType
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsMapperTest {

    private fun document(
        id: String,
        type: SalesDocumentType = SalesDocumentType.INVOICE,
        number: String = "INV-1",
        partyId: String? = "p1",
        dueDate: String? = "2026-10-10",
        status: String = "POSTED",
    ) = SalesDocument(
        id = id,
        type = type,
        number = number,
        partyId = partyId,
        dueDate = dueDate,
        status = status,
    )

    @Test
    fun overdueInvoiceMapsToPaymentOverdueNotification() {
        val notes = NotificationsMapper.overdueInvoices(
            listOf(document("d1")),
            mapOf("p1" to "Ali Autos"),
            "2026-10-27",
        )
        assertEquals(1, notes.size)
        assertEquals("overdue-d1", notes.single().id)
        assertEquals(NotificationType.PAYMENT_OVERDUE, notes.single().type)
        assertEquals("Ali Autos", notes.single().title)
        assertEquals("Invoice INV-1 overdue by 17 days — overdue", notes.single().detail)
    }

    @Test
    fun draftsFutureDueDatesMissingDueDatesAndNonInvoicesAreExcluded() {
        val docs = listOf(
            document("d1", status = "DRAFT"),
            document("d2", dueDate = "2026-11-01"),
            document("d3", dueDate = null),
            document("d4", type = SalesDocumentType.SALES_ORDER),
            document("d5", type = SalesDocumentType.QUOTATION),
        )
        assertEquals(
            emptyList<String>(),
            NotificationsMapper.overdueInvoices(docs, emptyMap(), "2026-10-27").map { it.id },
        )
    }

    @Test
    fun dueDateOnTodayIsNotOverdue() {
        val notes = NotificationsMapper.overdueInvoices(
            listOf(document("d1", dueDate = "2026-10-27")),
            emptyMap(),
            "2026-10-27",
        )
        assertTrue(notes.isEmpty())
    }

    @Test
    fun unknownPartyFallsBackToDocumentNumber() {
        val notes = NotificationsMapper.overdueInvoices(
            listOf(document("d1", partyId = "p9")),
            emptyMap(),
            "2026-10-27",
        )
        assertEquals("INV-1", notes.single().title)
    }

    @Test
    fun blankNumberFallsBackToDocumentId() {
        val notes = NotificationsMapper.overdueInvoices(
            listOf(document("d1", number = "", partyId = null)),
            emptyMap(),
            "2026-10-27",
        )
        assertEquals("d1", notes.single().title)
        assertEquals("Invoice d1 overdue by 17 days — overdue", notes.single().detail)
    }

    @Test
    fun daysOverdueAndBandBoundaries() {
        val cases = listOf(
            "2026-10-26" to "Invoice INV-1 overdue by 1 days — recently overdue",
            "2026-10-20" to "Invoice INV-1 overdue by 7 days — recently overdue",
            "2026-10-19" to "Invoice INV-1 overdue by 8 days — overdue",
            "2026-09-27" to "Invoice INV-1 overdue by 30 days — overdue",
            "2026-09-26" to "Invoice INV-1 overdue by 31 days — long overdue",
        )
        cases.forEach { (dueDate, expected) ->
            val notes = NotificationsMapper.overdueInvoices(
                listOf(document("d1", dueDate = dueDate)),
                emptyMap(),
                "2026-10-27",
            )
            assertEquals(expected, notes.single().detail)
        }
    }

    @Test
    fun unparseableDatesKeepTheOldDetailInsteadOfDroppingTheRow() {
        // "2026-02-30" passes the string filter (before today)
        // but is not a real calendar date, so parsing fails.
        val badDue = NotificationsMapper.overdueInvoices(
            listOf(document("d1", dueDate = "2026-02-30")),
            emptyMap(),
            "2026-10-27",
        )
        assertEquals(1, badDue.size)
        assertEquals("Invoice INV-1 overdue", badDue.single().detail)
        val badToday = NotificationsMapper.overdueInvoices(
            listOf(document("d1")),
            emptyMap(),
            "not-a-date",
        )
        assertEquals(1, badToday.size)
        assertEquals("Invoice INV-1 overdue", badToday.single().detail)
    }
}
