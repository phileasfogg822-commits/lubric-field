package com.lubrcapp.lubrcapp.feature.items

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
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
        override fun observe(scope: OperationalScope, table: String) = records
        override suspend fun list(scope: OperationalScope, table: String) = records.value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            if (failSave) throw IllegalStateException(\"storage unavailable\")
            records.value = records.value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deleted.add(id)
            records.value = records.value.filterNot { it.id == id }
        }
    }

    @Test fun emptyFormDisablesSaveAndBackNavigates() {
        val fake = FakeRepository()
        var backs = 0
        compose.setContent { ItemsScreen(scope, onBack = { backs++ }, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("Save item offline").assertIsNotEnabled()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun createEditAndConfirmDelete() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("Item name").performTextInput("Test item")
        compose.onNodeWithText("Save item offline").assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Edit Test item").performClick()
        compose.onNodeWithText("Update item offline").assertIsEnabled()
        compose.onNodeWithText("Delete Test item").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, fake.deleted.size) }
        compose.onNodeWithText("Delete Test item").performClick()
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
        compose.onNodeWithText("Item name").performTextInput("Sample")
        compose.onNodeWithText("Save item offline").performClick()
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("Unable to save item locally").assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
    }
}
