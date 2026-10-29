package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.designsystem.DateField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateFieldComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calendarIconOpensDialog() {
        compose.setContent {
            DateField(value = "", onValueChange = {}, label = "Date")
        }
        compose.onNodeWithContentDescription("Pick a date").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmWritesPreselectedDate() {
        var captured = ""
        compose.setContent {
            DateField(
                value = "2026-10-18",
                onValueChange = { captured = it },
                label = "Date",
            )
        }
        compose.onNodeWithContentDescription("Pick a date").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals("2026-10-18", captured)
    }

    @Test
    fun dismissClosesDialog() {
        compose.setContent {
            DateField(value = "", onValueChange = {}, label = "Date")
        }
        compose.onNodeWithContentDescription("Pick a date").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }
}
