package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemsScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val records = MutableStateFlow<List<OperationalRecord>>(emptyList())
        var failSave = false
        val deleted = mutableListOf<String>()
        var saves = 0
        override fun observe(scope: OperationalScope, table: String) = records
        override suspend fun list(scope: OperationalScope, table: String) = records.value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            if (failSave) throw IllegalStateException("storage unavailable")
            saves++
            records.value = records.value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deleted.add(id)
            records.value = records.value.filterNot { it.id == id }
        }
    }

    private fun openForm() {
        compose.onNodeWithText("Add item").performClick()
        compose.onNodeWithText("Save item").assertIsNotEnabled()
    }

    @Test fun listShowsAddItemAndBackNavigation() {
        val fake = FakeRepository()
        var backs = 0
        compose.setContent { ItemsScreen(scope, onBack = { backs++ }, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("Add item").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun createProductWithSaleRateAndStockThenEdit() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Engine Oil")
        compose.onNodeWithText("Sale rate").performScrollTo().performTextInput("1100")
        compose.onNodeWithText("Opening stock").performScrollTo().performTextInput("24")
        compose.onNodeWithText("Save item").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        // Back in list mode: name, sale rate and opening stock all show.
        compose.onNodeWithText("Engine Oil").assertIsDisplayed()
        compose.onNodeWithText("Sale rate: 1100").assertIsDisplayed()
        compose.onNodeWithText("Opening stock: 24").assertIsDisplayed()
        compose.onNodeWithText("Edit Engine Oil").performClick()
        compose.onNodeWithText("Item name").performTextReplacement("Engine Oil v2")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.saves == 2 }
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
        compose.onNodeWithText("Edit Engine Oil v2").assertIsDisplayed()
    }

    @Test fun createServiceItemShowsTypeInList() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Oil Change")
        compose.onNodeWithText("Service").performClick()
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Service").assertIsDisplayed()
        compose.runOnIdle {
            val saved = ItemJson.decode(fake.records.value.single().id, fake.records.value.single().json)
            assertEquals(ItemType.SERVICE, saved.type)
        }
    }

    @Test fun fullFieldSetSavesThroughCodec() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Full Item")
        compose.onNodeWithText("SKU").performScrollTo().performTextInput("SKU-1")
        compose.onNodeWithText("Barcode").performScrollTo().performTextInput("123456")
        compose.onNodeWithText("Purchase rate").performScrollTo().performTextInput("850.50")
        compose.onNodeWithText("Tax %").performScrollTo().performTextInput("18")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.runOnIdle {
            val saved = ItemJson.decode(fake.records.value.single().id, fake.records.value.single().json)
            assertEquals("SKU-1", saved.sku)
            assertEquals("123456", saved.barcode)
            assertEquals(BigDecimal("850.50"), saved.purchaseRate)
            assertEquals(BigDecimal("18"), saved.taxPercent)
        }
    }

    @Test fun invalidRateShowsFormErrorAndDoesNotSave() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Bad Numbers")
        compose.onNodeWithText("Sale rate").performScrollTo().performTextInput("soon")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.onNodeWithText("Rates, tax and opening stock must be numbers — check those fields.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, fake.saves) }
    }

    @Test fun createAndConfirmDelete() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Sample")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Delete Sample").performClick()
        compose.runOnIdle { assertEquals(0, fake.deleted.size) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete item?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete item?")[1].performClick()
        compose.waitUntil(5000) { fake.deleted.size == 1 }
        compose.runOnIdle { assertEquals(0, fake.records.value.size) }
    }

    @Test fun emptyStateIsShownWhenNoRecords() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("No items yet").assertIsDisplayed()
    }

    @Test fun saveFailureShowsError() {
        val fake = FakeRepository()
        fake.failSave = true
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Sample")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("Unable to save item locally").assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
    }
}
