package com.lubrcapp.lubrcapp.feature.printing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemedPrintPreviewTest {

    @get:Rule val compose = createComposeRule()

    private val rows = layoutThemedInvoice(
        printableDocument(
            title = "Invoice",
            documentNumber = "INV-1",
            date = "2026-10-18",
            partyName = "Ali Ahmed",
            lines = listOf(
                PrintableLine(
                    "Basmati Rice 5kg",
                    java.math.BigDecimal("2"),
                    java.math.BigDecimal("1650"),
                    java.math.BigDecimal("3300"),
                ),
            ),
            currency = "PKR",
        ),
    )

    @Test
    fun themedPreviewShowsHeaderAndTable() {
        compose.setContent {
            ThemedPrintPreviewScreen(
                headerName = "Demo General Store",
                headerDetail = null,
                photo = null,
                title = "Invoice",
                theme = PRINT_THEMES.first(),
                onThemeChange = {},
                rows = rows,
                onBack = {},
                onShare = {},
            )
        }
        compose.onNodeWithText("Demo General Store").assertIsDisplayed()
        compose.onNodeWithText("DG").assertIsDisplayed()
        compose.onNodeWithText("Item").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Basmati Rice 5kg").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun themeDotTapReportsTheme() {
        var selected: PrintTheme? = null
        compose.setContent {
            ThemedPrintPreviewScreen(
                headerName = "Demo General Store",
                headerDetail = null,
                photo = null,
                title = "Invoice",
                theme = PRINT_THEMES.first(),
                onThemeChange = { selected = it },
                rows = rows,
                onBack = {},
                onShare = {},
            )
        }
        compose.onNodeWithContentDescription("Red").performClick()
        assertEquals("red", selected?.id)
    }
}
