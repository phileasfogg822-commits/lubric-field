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
        private val tables = mutableMapOf<String, MutableStateFlow<List<OperationalRecord>>>()
        fun flow(table: String) = tables.getOrPut(table) { MutableStateFlow(emptyList()) }
        val records get() = flow("products")
        var failSave = false
        val deleted = mutableListOf<String>()
        var saves = 0
        override fun observe(scope: OperationalScope, table: String) = flow(table)
        override suspend fun list(scope: OperationalScope, table: String) = flow(table).value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            if (failSave) throw IllegalStateException("storage unavailable")
            saves++
            flow(table).value = flow(table).value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
        override suspend fun delete(scope: OperationalScope, table: String, id: String) {
            deleted.add(id)
            flow(table).value = flow(table).value.filterNot { it.id == id }
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

    @Test fun categoryLifecycleAssignCountAndOrphanAfterDelete() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        // Create the category.
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithText("No categories yet").assertIsDisplayed()
        compose.onNodeWithText("Add category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Oils")
        compose.onNodeWithText("Save category").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("categories").value.size == 1 }
        compose.onNodeWithText("Items: 0").assertIsDisplayed()
        // Back to items; create an item inside the category.
        compose.onNodeWithText("Back").performClick()
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Castrol Edge")
        compose.onNodeWithText("Oils").performScrollTo().performClick()
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Category: Oils").assertIsDisplayed()
        // The category now counts one item.
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithText("Items: 1").assertIsDisplayed()
        // Delete the category: the item keeps its link and shows it unresolved.
        compose.onNodeWithText("Delete Oils").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete category?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete category?")[1].performClick()
        compose.waitUntil(5000) { fake.flow("categories").value.isEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Category: —").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun subcategoryShowsParentLine() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithText("Add category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Oils")
        compose.onNodeWithText("Save category").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("categories").value.size == 1 }
        compose.onNodeWithText("Add category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Engine Oils")
        compose.onNodeWithText("Oils").performScrollTo().performClick()
        compose.onNodeWithText("Save category").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("categories").value.size == 2 }
        compose.onNodeWithText("Parent: Oils").assertIsDisplayed()
    }

    @Test fun brandLifecycleAssignCountAndOrphanAfterDelete() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        // Create the brand.
        compose.onNodeWithText("Brands").performClick()
        compose.onNodeWithText("No brands yet").assertIsDisplayed()
        compose.onNodeWithText("Add brand").performClick()
        compose.onNodeWithText("Brand name").performTextInput("Shell")
        compose.onNodeWithText("Save brand").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("brands").value.size == 1 }
        compose.onNodeWithText("Items: 0").assertIsDisplayed()
        // Back to items; create an item with the brand.
        compose.onNodeWithText("Back").performClick()
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Helix Ultra")
        compose.onNodeWithText("Shell").performScrollTo().performClick()
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Brand: Shell").assertIsDisplayed()
        // The brand now counts one item.
        compose.onNodeWithText("Brands").performClick()
        compose.onNodeWithText("Items: 1").assertIsDisplayed()
        // Delete the brand: the item keeps its link and shows it unresolved.
        compose.onNodeWithText("Delete Shell").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete brand?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete brand?")[1].performClick()
        compose.waitUntil(5000) { fake.flow("brands").value.isEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Brand: —").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun imageReferencesSaveThroughCodecAndShowInList() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Imaged Item")
        compose.onNodeWithText("Main image URI").performScrollTo().performTextInput("content://media/main.png")
        compose.onNodeWithText("Additional image URIs (one per line)").performScrollTo()
            .performTextInput("content://media/side.png")
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Image attached").assertIsDisplayed()
        compose.runOnIdle {
            val saved = ItemJson.decode(fake.records.value.single().id, fake.records.value.single().json)
            assertEquals("content://media/main.png", saved.mainImageUri)
            assertEquals(listOf("content://media/side.png"), saved.additionalImageUris)
        }
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

    @Test fun unitLifecycleAssignAndOrphanAfterDelete() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        // Create a unit from the suggested §25 names.
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("No units yet").assertIsDisplayed()
        compose.onNodeWithText("Add unit").performClick()
        compose.onNodeWithText("Carton").performClick()
        compose.onNodeWithText("Save unit").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("units").value.size == 1 }
        compose.onNodeWithText("Back").performClick()
        // Assign it to an item through the form's unit picker.
        openForm()
        compose.onNodeWithText("Item name").performTextInput("Oil Carton")
        compose.onNodeWithText("Carton").performScrollTo().performClick()
        compose.onNodeWithText("Save item").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Unit: Carton").assertIsDisplayed()
        // The unit counts the item; deleting it leaves the link unresolved.
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("Items: 1").assertIsDisplayed()
        compose.onNodeWithText("Delete Carton").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete unit?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete unit?")[1].performClick()
        compose.waitUntil(5000) { fake.flow("units").value.isEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Unit: —").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun conversionLifecycleShowsInUnitsMode() {
        val fake = FakeRepository()
        compose.setContent { ItemsScreen(scope, onBack = {}, viewModel = ItemsViewModel(fake)) }
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("Add unit").performClick()
        compose.onNodeWithText("Carton").performClick()
        compose.onNodeWithText("Save unit").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("units").value.size == 1 }
        compose.onNodeWithText("Add unit").performClick()
        compose.onNodeWithText("Bottle").performClick()
        compose.onNodeWithText("Save unit").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("units").value.size == 2 }
        // From = Carton (first list), To = Bottle (second list).
        compose.onNodeWithText("Add conversion").performClick()
        compose.onAllNodesWithText("Carton")[0].performClick()
        compose.onAllNodesWithText("Bottle")[1].performClick()
        compose.onNodeWithText("Factor").performTextInput("12")
        compose.onNodeWithText("Save conversion").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("unit_conversions").value.size == 1 }
        compose.onNodeWithText("Carton → Bottle: ×12").assertIsDisplayed()
        compose.runOnIdle {
            val record = fake.flow("unit_conversions").value.single()
            val saved = UnitConversionJson.decode(record.id, record.json)
            assertEquals(BigDecimal("12"), saved.factor)
        }
    }
}
