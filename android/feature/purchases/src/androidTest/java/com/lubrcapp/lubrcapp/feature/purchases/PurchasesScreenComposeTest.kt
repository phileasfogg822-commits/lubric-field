package com.lubrcapp.lubrcapp.feature.purchases

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.designsystem.BrandPalette
import com.lubrcapp.lubrcapp.core.designsystem.DesignSystemConfig
import com.lubrcapp.lubrcapp.core.designsystem.LubrcappTheme
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchasesScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val records = MutableStateFlow<List<OperationalRecord>>(emptyList())
        var failSave = false
        var saves = 0
        override fun observe(scope: OperationalScope, table: String) = records
        override suspend fun list(scope: OperationalScope, table: String) = records.value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            if (failSave) throw IllegalStateException("storage unavailable")
            saves++
            records.value = records.value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
    }

    @Test fun emptyFormAndBackNavigation() {
        val fake = FakeRepository()
        var backCount = 0
        val viewModel = PurchasesViewModel(fake)
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                PurchasesScreen(scope, onBack = { backCount++ }, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Save purchase").assertIsNotEnabled()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun createAndEditRecordOffline() {
        val fake = FakeRepository()
        val viewModel = PurchasesViewModel(fake)
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                PurchasesScreen(scope, onBack = {}, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Purchase").performTextInput("Sample")
        compose.onNodeWithText("Save purchase").assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Edit Sample").performClick()
        compose.onNodeWithText("Cancel edit").performClick()
        compose.runOnIdle { assertEquals(1, fake.saves) }
        compose.onNodeWithText("Edit Sample").performClick()
        compose.onNodeWithText("Save purchase").performClick()
        compose.waitUntil(5000) { fake.saves == 2 }
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun emptyStateIsShownWhenNoRecords() {
        val fake = FakeRepository()
        val viewModel = PurchasesViewModel(fake)
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                PurchasesScreen(scope, onBack = {}, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("No purchases yet").assertIsDisplayed()
    }

    @Test fun saveFailureShowsError() {
        val fake = FakeRepository()
        fake.failSave = true
        val viewModel = PurchasesViewModel(fake)
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                PurchasesScreen(scope, onBack = {}, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Purchase").performTextInput("Sample")
        compose.onNodeWithText("Save purchase").performClick()
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("Unable to save purchase locally").assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
    }
    private companion object {
        val testDesignSystemConfig = DesignSystemConfig(
            palette = BrandPalette(
                primary = 0xFF0F766E.toInt(),
                secondary = 0xFF14B8A6.toInt(),
                accent = 0xFFD97706.toInt(),
                success = 0xFF16A34A.toInt(),
                warning = 0xFFF59E0B.toInt(),
                error = 0xFFDC2626.toInt(),
            ),
        )
    }
}
