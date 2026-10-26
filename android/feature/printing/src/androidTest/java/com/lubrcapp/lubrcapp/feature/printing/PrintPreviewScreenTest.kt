package com.lubrcapp.lubrcapp.feature.printing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrintPreviewScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun document(itemCount: Int): PrintableDocument {
        val lines = (1..itemCount).map {
            PrintableLine("Item $it", BigDecimal("1"), BigDecimal("10"), BigDecimal("10"))
        }
        return printableDocument(
            title = "Invoice",
            documentNumber = "INV-1",
            date = "2026-10-26",
            lines = lines,
        )
    }

    @Test
    fun previewShowsTitleAndTotalAfterScroll() {
        compose.setContent {
            PrintPreviewScreen(
                document = document(30),
                onBack = {},
                onShare = {},
                onDownload = {},
                onPrint = {},
            )
        }
        compose.onNodeWithText("Invoice").assertIsDisplayed()
        compose.onNodeWithText("Total: 300").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actionButtonsFireCallbacks() {
        var share = 0
        var download = 0
        var print = 0
        var back = 0
        compose.setContent {
            PrintPreviewScreen(
                document = document(1),
                onBack = { back++ },
                onShare = { share++ },
                onDownload = { download++ },
                onPrint = { print++ },
            )
        }
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Download PDF").performClick()
        compose.onNodeWithText("Print").performClick()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, share)
        assertEquals(1, download)
        assertEquals(1, print)
        assertEquals(1, back)
    }

    @Test
    fun unwiredActionsAreHidden() {
        compose.setContent {
            PrintPreviewScreen(
                document = document(1),
                onBack = {},
                onShare = {},
                onDownload = null,
                onPrint = null,
            )
        }
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onAllNodesWithText("Download PDF").assertCountEquals(0)
        compose.onAllNodesWithText("Print").assertCountEquals(0)
    }
}
