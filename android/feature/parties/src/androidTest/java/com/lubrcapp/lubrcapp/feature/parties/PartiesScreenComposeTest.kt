package com.lubrcapp.lubrcapp.feature.parties

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class PartiesScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        private val tables = mutableMapOf<String, MutableStateFlow<List<OperationalRecord>>>()
        fun flow(table: String) = tables.getOrPut(table) { MutableStateFlow(emptyList()) }
        val records get() = flow("customers")
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
        compose.onNodeWithText("Add party").performClick()
        compose.onNodeWithText("Save party").assertIsNotEnabled()
    }

    @Test fun listShowsAddPartyAndBackNavigation() {
        val fake = FakeRepository()
        var backCount = 0
        compose.setContent { PartiesScreen(scope, onBack = { backCount++ }, viewModel = PartiesViewModel(fake)) }
        compose.onNodeWithText("Add party").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun createSupplierWithOpeningBalanceThenEdit() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Sample")
        compose.onNodeWithText("Supplier").performClick()
        compose.onNodeWithText("Opening balance").performScrollTo().performTextInput("250")
        compose.onNodeWithText("Save party").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        // Back in list mode: name, type and the payable direction all show.
        compose.onNodeWithText("Sample").assertIsDisplayed()
        compose.onNodeWithText("Payable: 250").assertIsDisplayed()
        compose.onNodeWithText("Edit Sample").performClick()
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.saves == 2 }
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun createPartyWithAddressSavesThroughCodec() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Addr Party")
        compose.onNodeWithText("Add address").performScrollTo().performClick()
        compose.onNodeWithText("City").performScrollTo().performTextInput("Lahore")
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.runOnIdle {
            val saved = PartyJson.decode(fake.records.value.single().id, fake.records.value.single().json)
            assertEquals(1, saved.addresses.size)
            assertEquals("Lahore", saved.addresses.single().city)
            assertEquals(PartyAddress.Kind.BILLING, saved.addresses.single().kind)
        }
    }

    @Test fun invalidAmountShowsFormErrorAndDoesNotSave() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Bad Numbers")
        compose.onNodeWithText("Credit days").performScrollTo().performTextInput("soon")
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.onNodeWithText("Amounts and credit days must be numbers — check those fields.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, fake.saves) }
    }

    @Test fun createAndConfirmDelete() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Sample")
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Delete Sample").performClick()
        compose.runOnIdle { assertEquals(0, fake.deleted.size) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete party?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete party?")[1].performClick()
        compose.waitUntil(5000) { fake.deleted.size == 1 }
        compose.runOnIdle { assertEquals(0, fake.records.value.size) }
    }

    @Test fun groupLifecycleAssignMemberAndOrphanAfterDelete() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        // Create the group.
        compose.onNodeWithText("Groups").performClick()
        compose.onNodeWithText("No groups yet").assertIsDisplayed()
        compose.onNodeWithText("Add group").performClick()
        compose.onNodeWithText("Group name").performTextInput("Retail")
        compose.onNodeWithText("Save group").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.flow("customer_groups").value.size == 1 }
        compose.onNodeWithText("Members: 0").assertIsDisplayed()
        // Back to parties; create a party inside the group.
        compose.onNodeWithText("Back").performClick()
        openForm()
        compose.onNodeWithText("Name").performTextInput("Grouped Party")
        compose.onNodeWithText("Retail").performScrollTo().performClick()
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("Group: Retail").assertIsDisplayed()
        // The group now counts one member.
        compose.onNodeWithText("Groups").performClick()
        compose.onNodeWithText("Members: 1").assertIsDisplayed()
        // Delete the group: the party keeps its link and shows it unresolved.
        compose.onNodeWithText("Delete Retail").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Delete group?").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Delete group?")[1].performClick()
        compose.waitUntil(5000) { fake.flow("customer_groups").value.isEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Group: —").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fake.records.value.size) }
    }

    @Test fun profileShowsCreditWarningAndFacts() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Credit Party")
        compose.onNodeWithText("Opening balance").performScrollTo().performTextInput("96")
        compose.onNodeWithText("Credit limit").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.records.value.size == 1 }
        compose.onNodeWithText("View Credit Party").performClick()
        compose.onNodeWithText("Outstanding: 96").assertIsDisplayed()
        compose.onNodeWithText("Credit limit: 100").assertIsDisplayed()
        compose.onNodeWithText("Near credit limit").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Add party").assertIsDisplayed()
    }

    @Test fun emptyStateIsShownWhenNoRecords() {
        val fake = FakeRepository()
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        compose.onNodeWithText("No parties yet").assertIsDisplayed()
    }

    @Test fun saveFailureShowsError() {
        val fake = FakeRepository()
        fake.failSave = true
        compose.setContent { PartiesScreen(scope, onBack = {}, viewModel = PartiesViewModel(fake)) }
        openForm()
        compose.onNodeWithText("Name").performTextInput("Sample")
        compose.onNodeWithText("Save party").performScrollTo().performClick()
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("Unable to save party locally").assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
    }
}
