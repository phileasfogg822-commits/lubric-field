package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportScreenTest {

    @get:Rule val compose = createComposeRule()

    private val csv = "Number,Date,Status\r\nINV-1,2026-10-27,DRAFT\r\n"

    @Test
    fun csvTextIsShown() {
        compose.setContent {
            ExportScreen(csvText = csv)
        }
        compose.onNodeWithText(csv).assertIsDisplayed()
    }

    @Test
    fun shareButtonFiresOnlyWhenSupplied() {
        var clicks = 0
        compose.setContent {
            ExportScreen(csvText = csv, onShare = { clicks += 1 })
        }
        compose.onNodeWithText("Share").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun shareButtonAbsentWithoutCallback() {
        compose.setContent {
            ExportScreen(csvText = csv)
        }
        compose.onAllNodesWithText("Share").assertCountEquals(0)
    }
}
