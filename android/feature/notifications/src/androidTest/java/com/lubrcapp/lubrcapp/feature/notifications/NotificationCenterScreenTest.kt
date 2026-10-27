package com.lubrcapp.lubrcapp.feature.notifications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.domain.business.Notification
import com.lubrcapp.lubrcapp.domain.business.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCenterScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun note(id: String, isRead: Boolean = false) = Notification(
        id = id,
        type = NotificationType.PAYMENT_OVERDUE,
        title = "Title $id",
        detail = "Detail $id",
        date = "2026-10-27",
        isRead = isRead,
    )

    @Test
    fun notificationsRenderWithTitleDetailAndDate() {
        compose.setContent {
            NotificationCenterScreen(listOf(note("a"), note("b", isRead = true)))
        }
        compose.onNodeWithText("Title a").assertIsDisplayed()
        compose.onNodeWithText("Detail b").assertIsDisplayed()
        compose.onNodeWithText("2026-10-27").assertIsDisplayed()
    }

    @Test
    fun emptyListShowsEmptyState() {
        compose.setContent { NotificationCenterScreen(emptyList()) }
        compose.onNodeWithText("No notifications").assertIsDisplayed()
    }

    @Test
    fun unreadCountShowsOnlyUnread() {
        compose.setContent {
            NotificationCenterScreen(listOf(note("a"), note("b", isRead = true), note("c")))
        }
        compose.onNodeWithText("2 unread").assertIsDisplayed()
    }

    @Test
    fun rowTapFiresMarkReadWithTheRowId() {
        var captured: String? = null
        compose.setContent {
            NotificationCenterScreen(listOf(note("a")), onMarkRead = { captured = it })
        }
        compose.onNodeWithTag("notification_a").performClick()
        assertEquals("a", captured)
    }
}
