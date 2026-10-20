package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
class SalesScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val records = MutableStateFlow<List<OperationalRecord>>(emptyList())
        var saves = 0
        override fun observe(scope: OperationalScope, table: String) = records
        override suspend fun list(scope: OperationalScope, table: String) = records.value
        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            saves++
            records.value = records.value.filterNot { it.id == id } + OperationalRecord(id, json)
        }
    }

    @Test fun emptyFormAndBackNavigation() {
        val fake = FakeRepository()
        var backCount = 0
        compose.setContent { SalesScreen(scope, onBack = { backCount++ }, viewModel = SalesViewModel(fake)) }
        compose.onNodeWithText("Save order").assertIsNotEnabled()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun createAndEditRecordOffline() {
        val fake = FakeRepository()
        compose.setContent { SalesScreen(scope, onBack = {}, viewModel = SalesViewModel(fake)) }
        compose.onNodeWithText("Order").performTextInput("Sample")
        compose.onNodeWithText("Save order").assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Edit Sample").performClick()
        compose.onNodeWithText("Cancel edit").performClick()
        compose.runOnIdle { assertEquals(1, fake.saves) }
        compose.onNodeWithText("Edit Sample").performClick()
        compose.onNodeWithText("Save order").performClick()
        compose.waitUntil(5000) { fake.saves == 2 }
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }
}
