package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 29.1 — notification model (§104, §165).
 * Pure model over plain values: evaluators (29.4)
 * and the center screen (29.5) build [Notification]
 * values from current data; this core never touches
 * a repository, posts to a system tray, or moves
 * money or stock.
 *
 * Types are exactly the §104 list, in spec order.
 * Grouping (§165) is a display concern: [NotificationOps]
 * filters and orders, the screen groups by day label.
 * Dates are ISO `yyyy-MM-dd` text, so lexicographic
 * comparison is chronological.
 */
enum class NotificationType {
    LOW_STOCK,
    ORDER_RECEIVED,
    ORDER_APPROVED,
    PAYMENT_OVERDUE,
    PAYMENT_RECEIVED,
    BACKUP_COMPLETED,
    SYNC_ERROR,
    STOCK_EXPIRY,
    PURCHASE_DUE,
    CREDIT_LIMIT_WARNING,
}

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val detail: String = "",
    val date: String = "",
    val isRead: Boolean = false,
)

object NotificationOps {
    fun unread(notifications: List<Notification>): List<Notification> =
        notifications.filter { !it.isRead }

    fun unreadCount(notifications: List<Notification>): Int =
        notifications.count { !it.isRead }

    fun byType(
        notifications: List<Notification>,
        type: NotificationType,
    ): List<Notification> = notifications.filter { it.type == type }

    fun markRead(notification: Notification): Notification =
        notification.copy(isRead = true)

    fun markAllRead(notifications: List<Notification>): List<Notification> =
        notifications.map { it.copy(isRead = true) }

    /** Newest first by date, then by id for a stable order. */
    fun newestFirst(notifications: List<Notification>): List<Notification> =
        notifications.sortedWith(compareByDescending<Notification> { it.date }.thenBy { it.id })
}
