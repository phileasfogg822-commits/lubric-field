package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTest {

    private fun note(
        id: String,
        type: NotificationType = NotificationType.LOW_STOCK,
        date: String = "2026-10-27",
        isRead: Boolean = false,
    ) = Notification(id = id, type = type, title = "t-$id", date = date, isRead = isRead)

    @Test
    fun typesAreExactlyTheSpecListInOrder() {
        assertEquals(
            listOf(
                NotificationType.LOW_STOCK,
                NotificationType.ORDER_RECEIVED,
                NotificationType.ORDER_APPROVED,
                NotificationType.PAYMENT_OVERDUE,
                NotificationType.PAYMENT_RECEIVED,
                NotificationType.BACKUP_COMPLETED,
                NotificationType.SYNC_ERROR,
                NotificationType.STOCK_EXPIRY,
                NotificationType.PURCHASE_DUE,
                NotificationType.CREDIT_LIMIT_WARNING,
            ),
            NotificationType.entries.toList(),
        )
    }

    @Test
    fun unreadFiltersReadNotifications() {
        val notes = listOf(note("a"), note("b", isRead = true), note("c"))
        assertEquals(listOf("a", "c"), NotificationOps.unread(notes).map { it.id })
        assertEquals(2, NotificationOps.unreadCount(notes))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertTrue(NotificationOps.unread(emptyList()).isEmpty())
        assertEquals(0, NotificationOps.unreadCount(emptyList()))
        assertTrue(NotificationOps.byType(emptyList(), NotificationType.SYNC_ERROR).isEmpty())
        assertTrue(NotificationOps.markAllRead(emptyList()).isEmpty())
        assertTrue(NotificationOps.newestFirst(emptyList()).isEmpty())
    }

    @Test
    fun byTypeKeepsOnlyThatType() {
        val notes = listOf(
            note("a", NotificationType.PAYMENT_OVERDUE),
            note("b", NotificationType.LOW_STOCK),
            note("c", NotificationType.PAYMENT_OVERDUE),
        )
        assertEquals(
            listOf("a", "c"),
            NotificationOps.byType(notes, NotificationType.PAYMENT_OVERDUE).map { it.id },
        )
    }

    @Test
    fun markReadSetsTheFlagAndKeepsEverythingElse() {
        val read = NotificationOps.markRead(note("a", detail = "d"))
        assertTrue(read.isRead)
        assertEquals("d", read.detail)
        assertEquals("a", read.id)
    }

    @Test
    fun markReadOnAnAlreadyReadNotificationIsANoOp() {
        assertEquals(note("a", isRead = true), NotificationOps.markRead(note("a", isRead = true)))
    }

    @Test
    fun markAllReadReadsEveryNotification() {
        val notes = listOf(note("a"), note("b", isRead = true))
        assertTrue(NotificationOps.markAllRead(notes).all { it.isRead })
    }

    @Test
    fun newestFirstOrdersByDateDescendingThenId() {
        val notes = listOf(
            note("b", date = "2026-10-25"),
            note("a", date = "2026-10-27"),
            note("c", date = "2026-10-27"),
        )
        assertEquals(
            listOf("a", "c", "b"),
            NotificationOps.newestFirst(notes).map { it.id },
        )
    }
}
