package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class PartyLedgerTabsComposeTest {

    @get:Rule val compose = createComposeRule()

    private val tabs = PartyLedgerTabsData(
        transactions = listOf(
            PartyLedgerRow("2026-10-01", "INV-2026-00001", "Invoice", "100000"),
            PartyLedgerRow("2026-10-05", "PAY-2026-00001", "Payment", "-40000"),
        ),
        statement = listOf(
            PartyStatementRow("2026-10-01", "INV-2026-00001", "Invoice", "100000", "100000"),
            PartyStatementRow("2026-10-05", "PAY-2026-00001", "Payment", "-40000", "60000"),
        ),
        payments = listOf(
            PartyLedgerRow("2026-10-05", "PAY-2026-00001", "Payment", "-40000"),
        ),
        orders = listOf(
            PartyOrderRow("SO-2026-00001", "2026-09-28", "CONFIRMED", "100000"),
        ),
        items = listOf(
            PartyItemRow("Engine Oil 4L", "2", "20000"),
        ),
    )

    private fun openProfile() {
        compose.setContent {
            PartyProfileMode(
                party = Party(id = "party-1", name = "Ali Autos"),
                groupName = null,
                onBack = {},
                onEdit = {},
                ledgerTabs = tabs,
            )
        }
    }

    @Test fun transactionsTabShowsByDefault() {
        openProfile()
        compose.onNodeWithText("2026-10-01 INV-2026-00001 100000").assertIsDisplayed()
        // The statement's running-balance line is not visible on this tab.
        compose.onAllNodesWithText("2026-10-05 PAY-2026-00001 -40000 60000").assertCountEquals(0)
    }

    @Test fun paymentsTabSwitchesContent() {
        openProfile()
        compose.onNodeWithText("Payments").performScrollTo().performClick()
        compose.onNodeWithText("2026-10-05 PAY-2026-00001 -40000").assertIsDisplayed()
        // The invoice-only transaction row leaves with the tab switch.
        compose.onAllNodesWithText("2026-10-01 INV-2026-00001 100000").assertCountEquals(0)
    }

    @Test fun ordersAndItemsTabsShowTheirRows() {
        openProfile()
        compose.onNodeWithText("Orders").performScrollTo().performClick()
        compose.onNodeWithText("SO-2026-00001 2026-09-28 CONFIRMED 100000").assertIsDisplayed()
        compose.onNodeWithText("Items").performScrollTo().performClick()
        compose.onNodeWithText("Engine Oil 4L 2 20000").assertIsDisplayed()
    }

    @Test fun noTabsWithoutLedgerData() {
        compose.setContent {
            PartyProfileMode(
                party = Party(id = "party-1", name = "Ali Autos"),
                groupName = null,
                onBack = {},
                onEdit = {},
            )
        }
        // Header-only profile, exactly as 14.4 left it: no tab buttons.
        compose.onAllNodesWithText("Transactions").assertCountEquals(0)
        compose.onAllNodesWithText("Payments").assertCountEquals(0)
    }
}
