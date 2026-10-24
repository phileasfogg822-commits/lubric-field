package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.runBlocking
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
        compose.onNodeWithTag("salesSaveButton").assertIsEnabled().performClick()
        try {
            compose.waitUntil(10000) { fake.orders.value.size == 1 }
        } catch (e: ComposeTimeoutException) {
            // Diagnostic: pinpoint the UI state when the save stalls.
            // (fetchSemanticsNodes is not in this project's compose-ui-test;
            // use assertCountEquals probes in try/catch instead.)
            fun exists(text: String, substring: Boolean = false): Boolean = try {
                compose.onAllNodesWithText(text, substring = substring).assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
            val errorShown = exists("Unable to save locally", substring = true)
            val saveStillThere = try {
                compose.onAllNodesWithTag("salesSaveButton").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
            val backInList = exists("New quotation")
            throw AssertionError(
                "Save timed out: orders=${fake.orders.value.size}, " +
                "numbering_sequences=${fake.flow("numbering_sequences").value.size}, " +
                "order_items=${fake.flow("order_items").value.size}, " +
                "timeline_entries=${fake.flow("timeline_entries").value.size}; " +
                "errorShown=$errorShown, saveStillThere=$saveStillThere, backInList=$backInList"
            )
        }
        // Back in list mode, the saved row carries the issued number.
        compose.onNodeWithText("QT-2026-00001 · Quotation · DRAFT · 2026-10-23").assertIsDisplayed()
    }

    @Test
    fun listLabelsInvoicesReturnsAndCreditNotesByType() {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        runBlocking {
            vm.saveDocument(scope, SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, number = "INV-2026-00001", status = "SENT", date = "2026-10-24"))
            vm.saveDocument(scope, SalesDocument(id = "ret-1", type = SalesDocumentType.SALES_RETURN, number = "SR-2026-00001", status = "SENT", date = "2026-10-24"))
            vm.saveDocument(scope, SalesDocument(id = "cn-1", type = SalesDocumentType.CREDIT_NOTE, number = "CN-2026-00001", status = "SENT", date = "2026-10-24"))
        }
        compose.setContent { SalesScreen(scope, onBack = {}, viewModel = vm) }
        compose.onAllNodesWithText("INV-2026-00001 · Invoice · SENT · 2026-10-24").assertCountEquals(1)
        compose.onAllNodesWithText("SR-2026-00001 · Sales return · SENT · 2026-10-24").assertCountEquals(1)
        compose.onAllNodesWithText("CN-2026-00001 · Credit note · SENT · 2026-10-24").assertCountEquals(1)
    }

    @Test
    fun invoiceEditorShowsNoOrderStatusButtons() {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        runBlocking {
            vm.saveDocument(scope, SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, number = "INV-2026-00001", status = "SENT", date = "2026-10-24"))
        }
        compose.setContent { SalesScreen(scope, onBack = {}, viewModel = vm) }
        compose.onNodeWithText("INV-2026-00001 · Invoice · SENT · 2026-10-24").performClick()
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Convert to return").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // The invoice's lifecycle moves through posting and
        // payments, not through the order transitions.
        compose.onAllNodesWithText("Mark ", substring = true).assertCountEquals(0)
    }

    @Test
    fun convertConfirmedOrderToInvoiceOpensTheInvoice() {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        runBlocking {
            vm.saveDocumentWithLines(
                scope,
                SalesDocument(id = "o-1", type = SalesDocumentType.SALES_ORDER, number = "SO-2026-00003", status = "CONFIRMED", date = "2026-10-24"),
                listOf(SalesDocumentLine(id = "l-1", orderId = "o-1", position = 0, quantity = BigDecimal("1"), rate = BigDecimal("100"))),
            )
        }
        compose.setContent { SalesScreen(scope, onBack = {}, viewModel = vm) }
        compose.onNodeWithText("SO-2026-00003 · Sales order · CONFIRMED · 2026-10-24").performClick()
        // The editor opens asynchronously (openDocument loads
        // lines in a coroutine): wait for it before scrolling.
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Convert to invoice").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Convert to invoice").performScrollTo().performClick()
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Invoice").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun postInvoicePickerCallsHandlerWithPickedAccountsAndShowsNotice() {
        val fake = FakeRepository()
        val vm = SalesViewModel(fake)
        runBlocking {
            vm.saveDocumentWithLines(
                scope,
                SalesDocument(id = "inv-1", type = SalesDocumentType.INVOICE, number = "INV-2026-00001", status = "SENT", date = "2026-10-24"),
                listOf(SalesDocumentLine(id = "l-1", orderId = "inv-1", position = 0, quantity = BigDecimal("1"), rate = BigDecimal("100"))),
            )
        }
        val posted = mutableListOf<Triple<String, InvoicePostingAccounts, String>>()
        compose.setContent {
            SalesScreen(
                scope,
                onBack = {},
                viewModel = vm,
                accountOptions = listOf(
                    PostingAccountOption("acc-recv", "Recv Acct"),
                    PostingAccountOption("acc-rev", "Rev Acct"),
                    PostingAccountOption("acc-tax", "Tax Acct"),
                ),
                onPostDocument = { document, postingAccounts, currency ->
                    posted += Triple(document.id, postingAccounts, currency.value)
                    SalesPostResult.Posted(document.number, emptyList())
                },
            )
        }
        compose.onNodeWithText("INV-2026-00001 · Invoice · SENT · 2026-10-24").performClick()
        // The editor opens asynchronously: wait for it before
        // reaching for the Post button.
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Post invoice").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithText("Post invoice").performScrollTo().performClick()
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Posting accounts").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // Each account name appears once per picker group, so
        // pick by group position: receivable's Recv is first,
        // revenue's Rev is second.
        compose.onAllNodesWithText("Recv Acct")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Rev Acct")[1].performScrollTo().performClick()
        compose.onNodeWithText("Confirm posting").performScrollTo().performClick()
        compose.waitUntil(10000) {
            try {
                compose.onAllNodesWithText("Posted INV-2026-00001").assertCountEquals(1)
                true
            } catch (_: AssertionError) {
                false
            }
        }
        assertEquals(1, posted.size)
        assertEquals("inv-1", posted[0].first)
        assertEquals("acc-recv", posted[0].second.receivableAccountId)
        assertEquals("acc-rev", posted[0].second.revenueAccountId)
        assertEquals(null, posted[0].second.taxAccountId)
        assertEquals("XXX", posted[0].third)
    }
}
