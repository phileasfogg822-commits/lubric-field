package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 18.4 — replaces the Phase 12 raw-record screen tests:
 * the raw screen is gone, and with it the description-only
 * flows those tests pinned.
 */
@RunWith(AndroidJUnit4::class)
class SalesScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        private val tables = mutableMapOf<String, MutableStateFlow<List<OperationalRecord>>>()
        fun flow(table: String) = tables.getOrPut(table) { MutableStateFlow(emptyList()) }
        val orders get() = flow("orders")
        override fun observe(scope: OperationalScope, table: String) = flow(table)
        override suspend fun list(scope: OperationalScope, table: String) = flow(table).value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            flow(table).value = flow(table).value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            flow(table).value = flow(table).value.filterNot { it.id == id }
        }
    }

    @Test fun listShowsCreateActionsAndBackNavigation() {
        val fake = FakeRepository()
        var backs = 0
        compose.setContent { SalesScreen(scope, onBack = { backs++ }, viewModel = SalesViewModel(fake)) }
        compose.onNodeWithText("New quotation").assertIsDisplayed()
        compose.onNodeWithText("New sales order").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun createQuotationShowsLiveTotalsAndSavesNumbered() {
        val fake = FakeRepository()
        compose.setContent { SalesScreen(scope, onBack = {}, viewModel = SalesViewModel(fake)) }
        compose.onNodeWithText("New quotation").performClick()
        compose.onNodeWithText("Date (yyyy-MM-dd)").performTextInput("2026-10-23")
        compose.onNodeWithText("Description").performScrollTo().performTextInput("Engine Oil")
        compose.onNodeWithText("Quantity").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Rate").performScrollTo().performTextInput("500")
        compose.onNodeWithText("Tax %").performScrollTo().performTextInput("17")
        // 2 x 500 exclusive + 17% tax = 1170, computed live by the engine
        // (Money strips trailing zeros; the app displays toPlainString).
        // assertCountEquals, not assertIsDisplayed: the exact text match
        // proves the engine totals are live; on-screen visibility flaps
        // against the focused text field's bring-into-view fighting the
        // test's scroll. (assertExists is not importable in this
        // project's compose-ui-test version — unresolved reference at
        // compile; assertCountEquals is the proven pattern here.)
        compose.onAllNodesWithText("Grand total: 1170").assertCountEquals(1)
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.orders.value.size == 1 }
        // Back in list mode, the saved row carries the issued number.
        compose.onNodeWithText("QT-2026-00001 · Quotation · DRAFT · 2026-10-23").assertIsDisplayed()
    }
}
