package com.lubrcapp.lubrcapp.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.domain.business.Notification

/**
 * Phase 29.5 — the notification center screen (§165).
 * Stateless over the notifications the shell
 * already built: the screen only renders. An
 * empty list shows the empty state, never a
 * count. Rows scroll in a weight(1f) Box; the
 * title and unread count stay fixed, so tests
 * never scroll for them. A row is clickable only
 * when [onMarkRead] is supplied (the 25.6
 * nullable-callback pattern) — without a
 * callback the rows are display-only.
 */
@Composable
fun NotificationCenterScreen(
    notifications: List<Notification>,
    onMarkRead: ((String) -> Unit)? = null,
) {
    val unread = notifications.count { !it.isRead }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.notification_center_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (unread > 0) {
            Text(stringResource(R.string.notification_unread_count, unread))
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notifications.isEmpty()) {
                    Text(stringResource(R.string.notification_empty))
                } else {
                    notifications.forEach { notification ->
                        val rowModifier = Modifier.fillMaxWidth()
                            .testTag("notification_${notification.id}")
                        Column(
                            modifier = if (onMarkRead != null) {
                                rowModifier.clickable { onMarkRead(notification.id) }
                            } else {
                                rowModifier
                            },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                notification.title,
                                fontWeight = if (notification.isRead) {
                                    FontWeight.Normal
                                } else {
                                    FontWeight.Bold
                                },
                            )
                            if (notification.detail.isNotBlank()) {
                                Text(notification.detail)
                            }
                            if (notification.date.isNotBlank()) {
                                Text(notification.date)
                            }
                            if (!notification.isRead) {
                                Text(stringResource(R.string.notification_unread))
                            }
                        }
                    }
                }
            }
        }
    }
}
