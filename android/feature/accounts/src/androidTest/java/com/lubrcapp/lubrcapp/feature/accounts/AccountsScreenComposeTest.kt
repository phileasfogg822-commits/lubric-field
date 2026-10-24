package com.lubrcapp.lubrcapp.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.repository.OperationalBusinessRepository
import com.lubrcapp.lubrcapp.core.repository.OperationalRecord
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsScreenComposeTest {
    @get:Rule val compose = createComposeRule()
    private val scope = OperationalScope("tenant-a", "business-a", "device-a")

    private class FakeRepository : OperationalBusinessRepository {
        val state = MutableStateFlow<List<Pair<String, OperationalRecord>>>(emptyList())

        override fun observe(scope: OperationalScope, table: String): Flow<List<OperationalRecord>> =
            state.map { rows -> rows.filter { it.first == table }.map { it.second } }

        override suspend fun list(scope: OperationalScope, table: String): List<OperationalRecord> =
            state.value.filter { it.first == table }.map { it.second }

        override suspend fun save(scope: OperationalScope, table: String, id: String, json: String, revision: Long) {
            state.value = state.value.filterNot { it.first == table && it.second.id == id } +
                (table to OperationalRecord(id, json))
        }
    }

    private fun FakeRepository.seedAccount(account: AccountRecord) {
        state.value = state.value + ("accounts" to OperationalRecord(account.id, AccountJson.encode(account)))
    }

    private fun FakeRepository.seedEntry(entry: JournalEntryRecord) {
        state.value = state.value + ("journal_entries" to OperationalRecord(entry.id, JournalEntryJson.encode(entry)))
    }

    @Test fun chartAddAccountFlow() {
        val fake = FakeRepository()
        compose.setContent { AccountsScreen(scope, onBack = {}, viewModel = AccountsViewModel(fake)) }
        compose.onNodeWithText("Code").performScrollTo().performTextInput("1000")
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Cash")
        compose.onNodeWithText("ASSET").performScrollTo().performClick()
        compose.onNodeWithText("Save account").performScrollTo().performClick()
        compose.waitUntil(5000) { fake.state.value.any { it.first == "accounts" } }
        compose.onNodeWithText("1000 · Cash · ASSET").assertIsDisplayed()
    }

    @Test fun chartDeactivateMarksRowInactive() {
        val fake = FakeRepository()
        fake.seedAccount(AccountRecord("acc-1", "1000", "Cash", AccountType.ASSET, null, true, ""))
        compose.setContent { AccountsScreen(scope, onBack = {}, viewModel = AccountsViewModel(fake)) }
        compose.onNodeWithText("1000 · Cash · ASSET").assertIsDisplayed()
        compose.onNodeWithText("Deactivate").performScrollTo().performClick()
        compose.waitUntil(5000) {
            fake.state.value.any { it.first == "accounts" && !AccountJson.decode(it.second.id, it.second.json).isActive }
        }
        compose.onNodeWithText("1000 · Cash · ASSET · Inactive").assertIsDisplayed()
    }

    @Test fun journalTrialBalanceAndDetailFlow() {
        val fake = FakeRepository()
        fake.seedAccount(AccountRecord("cash", "1000", "Cash", AccountType.ASSET, null, true, ""))
        fake.seedAccount(AccountRecord("sales", "4000", "Sales", AccountType.INCOME, null, true, ""))
        fake.seedEntry(
            JournalEntryRecord(
                id = "je-1",
                date = "2026-10-24",
                description = "Credit sale",
                referenceNumber = "INV-2026-00001",
                lines = listOf(
                    JournalLineRecord("cash", BigDecimal("100000"), BigDecimal.ZERO),
                    JournalLineRecord("sales", BigDecimal.ZERO, BigDecimal("100000")),
                ),
                createdAt = "2026-10-24T00:00:00Z",
            ),
        )
        compose.setContent { AccountsScreen(scope, onBack = {}, viewModel = AccountsViewModel(fake)) }
        compose.onNodeWithText("Journal").performClick()
        compose.onNodeWithText("Total debits 100000").assertIsDisplayed()
        compose.onNodeWithText("Total credits 100000").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertIsDisplayed()
        compose.onNodeWithText("2026-10-24 · Credit sale · Dr 100000 / Cr 100000").performScrollTo().performClick()
        compose.onNodeWithText("Cash · Dr 100000 / Cr 0").assertIsDisplayed()
        compose.onNodeWithText("Sales · Dr 0 / Cr 100000").assertIsDisplayed()
        compose.onNodeWithText("Back to journal").performClick()
        compose.onNodeWithText("2026-10-24 · Credit sale · Dr 100000 / Cr 100000").assertIsDisplayed()
    }

    @Test fun emptyStatesAndBackNavigation() {
        val fake = FakeRepository()
        var backCount = 0
        compose.setContent { AccountsScreen(scope, onBack = { backCount++ }, viewModel = AccountsViewModel(fake)) }
        compose.onNodeWithText("No accounts yet").assertIsDisplayed()
        compose.onNodeWithText("Journal").performClick()
        compose.onNodeWithText("No journal entries yet").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun saveButtonDisabledUntilCodeAndNameEntered() {
        val fake = FakeRepository()
        compose.setContent { AccountsScreen(scope, onBack = {}, viewModel = AccountsViewModel(fake)) }
        compose.onNodeWithText("Save account").assertIsNotEnabled()
        compose.onNodeWithText("Code").performScrollTo().performTextInput("1000")
        compose.onNodeWithText("Save account").assertIsNotEnabled()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Cash")
        compose.onNodeWithText("Save account").assertIsEnabled()
    }
}
