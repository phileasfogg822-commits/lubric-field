package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 27.7 — the salesperson dashboard screen renders
 * its summary rows and every supplied action as a
 * callback, and omits null rows and null actions.
 */
@RunWith(AndroidJUnit4::class)
class SalespersonDashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val pkr = CurrencyCode("PKR")

    private fun summary() = FieldSalesSummary(
        todaySales = Money.of("1300", pkr),
        orders = 3,
        collections = Money.of("2500", pkr),
        targetPercent = Percentage.of("72"),
    )

    private fun setContent(
        summary: FieldSalesSummary = summary(),
        onTakeOrder: (() -> Unit)? = null,
        onTargets: (() -> Unit)? = null,
    ) {
        compose.setContent {
            SalespersonDashboardScreen(
                summary = summary,
                onBack = {},
                onTakeOrder = onTakeOrder,
                onTargets = onTargets,
            )
        }
    }

    @Test
    fun rendersSummaryRows() {
        setContent()
        compose.onNodeWithText("Today's sales: 1300").assertIsDisplayed()
        compose.onNodeWithText("Orders: 3").assertIsDisplayed()
        compose.onNodeWithText("Collections: 2500").assertIsDisplayed()
        compose.onNodeWithText("Target: 72%").assertIsDisplayed()
    }

    @Test
    fun omitsNullRowsAndNullActions() {
        setContent(summary().copy(collections = null, targetPercent = null))
        compose.onAllNodesWithText("Collections: 2500").assertCountEquals(0)
        compose.onAllNodesWithText("Target: 72%").assertCountEquals(0)
        compose.onAllNodesWithText("Take Order").assertCountEquals(0)
        compose.onAllNodesWithText("Targets").assertCountEquals(0)
    }

    @Test
    fun actionButtonsInvokeCallbacks() {
        var takeOrderClicked = false
        var targetsClicked = false
        setContent(
            onTakeOrder = { takeOrderClicked = true },
            onTargets = { targetsClicked = true },
        )
        compose.onNodeWithText("Take Order").performClick()
        compose.onNodeWithText("Targets").performClick()
        assertEquals(true, takeOrderClicked)
        assertEquals(true, targetsClicked)
    }
}
