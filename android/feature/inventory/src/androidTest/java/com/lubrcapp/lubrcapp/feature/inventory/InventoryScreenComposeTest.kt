package com.lubrcapp.lubrcapp.feature.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val tables = mutableMapOf<String, MutableStateFlow<List<OperationalRecord>>>()
        fun flowFor(table: String) = tables.getOrPut(table) { MutableStateFlow(emptyList()) }
        fun seed(table: String, records: List<OperationalRecord>) {
            flowFor(table).value = records
        }
        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> = flowFor(table)
        override suspend fun list(scope: OperationalScope, table: String) = flowFor(table).value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            val flow = flowFor(table)
            flow.value = flow.value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
    }

    @Test fun cardsAndBackNavigation() {
        val fake = FakeRepository()
        var backCount = 0
        compose.setContent { InventoryScreen(scope, onBack = { backCount++ }, viewModel = InventoryViewModel(fake)) }
        compose.onNodeWithText("Inventory value: 0").assertIsDisplayed()
        compose.onNodeWithText("Items: 0").assertIsDisplayed()
        compose.onNodeWithText("No items yet").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun itemRowShowsLedgerBalanceAndDetailShowsHistory() {
        val fake = FakeRepository()
        fake.seed(
            "products",
            listOf(OperationalRecord("oil", """{"name":"Engine Oil","purchaseRate":"100","openingStock":"50"}""")),
        )
        val movement = StockMovementRecord.of(
            id = "mv-1", itemId = "oil", type = MovementType.SALE,
            quantity = BigDecimal("5"), date = "2026-10-24", reason = "Sold",
        )
        fake.seed("stock_movements", listOf(OperationalRecord("mv-1", StockMovementJson.encode(movement))))
        compose.setContent { InventoryScreen(scope, onBack = {}, viewModel = InventoryViewModel(fake)) }
        compose.onNodeWithText("Engine Oil · 45 · In stock").assertIsDisplayed()
        compose.onNodeWithText("Inventory value: 4500").assertIsDisplayed()
        compose.onNodeWithText("Engine Oil · 45 · In stock").performClick()
        compose.onNodeWithText("Balance: 45").assertIsDisplayed()
        compose.onNodeWithText("2026-10-24 · SALE · -5").assertIsDisplayed()
        compose.onNodeWithText("Back to list").performClick()
        compose.onNodeWithText("Engine Oil · 45 · In stock").assertIsDisplayed()
    }

    @Test fun adjustFlowPostsAMovement() {
        val fake = FakeRepository()
        fake.seed(
            "products",
            listOf(OperationalRecord("oil", """{"name":"Engine Oil","openingStock":"50"}""")),
        )
        compose.setContent { InventoryScreen(scope, onBack = {}, viewModel = InventoryViewModel(fake)) }
        compose.onNodeWithText("Adjust stock").performClick()
        compose.onNodeWithText("Engine Oil").performClick()
        compose.onNodeWithText("Quantity").performScrollTo().performTextInput("3")
        compose.onNodeWithText("Reason").performScrollTo().performTextInput("Leaking cans")
        compose.onNodeWithText("Post adjustment").performClick()
        compose.waitUntil(5000) { (fake.flowFor("stock_movements").value.size) == 1 }
        compose.onNodeWithText("Adjustment posted").assertIsDisplayed()
        val saved = StockMovementJson.decode(
            fake.flowFor("stock_movements").value[0].id,
            fake.flowFor("stock_movements").value[0].json,
        )
        assertEquals(0, BigDecimal("3").compareTo(saved.quantity))
        assertEquals("Leaking cans", saved.reason)
    }
}
