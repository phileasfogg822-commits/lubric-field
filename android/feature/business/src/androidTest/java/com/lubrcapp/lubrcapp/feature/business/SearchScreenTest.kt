package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.domain.business.SearchEntry
import com.lubrcapp.lubrcapp.domain.business.SearchGroup
import com.lubrcapp.lubrcapp.domain.business.SearchGroupResults
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun results(): List<SearchGroupResults> = listOf(
        SearchGroupResults(
            SearchGroup.INVOICES,
            listOf(SearchEntry(SearchGroup.INVOICES, "d1", "INV-1", "INVOICE · 2026-10-27 · DRAFT")),
        ),
    )

    @Test
    fun groupedResultsRender() {
        compose.setContent {
            SearchScreen(query = "oil", results = results(), onQueryChange = {})
        }
        compose.onNodeWithText("Invoices").assertIsDisplayed()
        compose.onNodeWithText("INV-1").assertIsDisplayed()
    }

    @Test
    fun blankQueryShowsNothing() {
        compose.setContent {
            SearchScreen(query = "", results = emptyList(), onQueryChange = {})
        }
        compose.onAllNodesWithText("No results").assertCountEquals(0)
    }

    @Test
    fun nonBlankQueryWithNoGroupsShowsEmptyState() {
        compose.setContent {
            SearchScreen(query = "zzz", results = emptyList(), onQueryChange = {})
        }
        compose.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun typingFiltersThroughTheCallback() {
        var query by mutableStateOf("")
        compose.setContent {
            val shown = if (query.isBlank()) emptyList() else results()
            SearchScreen(query = query, results = shown, onQueryChange = { query = it })
        }
        compose.onNodeWithText("Search customers, items, invoices…").performTextInput("oil")
        compose.onNodeWithText("INV-1").assertIsDisplayed()
    }
}
