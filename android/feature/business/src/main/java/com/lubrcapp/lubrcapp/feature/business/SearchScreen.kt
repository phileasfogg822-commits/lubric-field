package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.domain.business.SearchGroup
import com.lubrcapp.lubrcapp.domain.business.SearchGroupResults

/**
 * Phase 28.7 — the global search screen (§129). It
 * is stateless over the query and the grouped
 * results 28.1's [GlobalSearch] already computed:
 * the shell owns the record mapping and the
 * search call, this screen only renders. A blank
 * query shows nothing in the results area — not
 * everything, not a "no results" message — because
 * 28.1 returns no groups for a blank needle. A
 * non-blank query with no groups shows the empty
 * state. Results scroll in a weight(1f) Box; the
 * query field stays fixed, so tests never scroll
 * for it.
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<SearchGroupResults>,
    onQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.data_search_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.data_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isNotBlank() && results.isEmpty()) {
                    Text(stringResource(R.string.data_search_empty))
                }
                results.forEach { group ->
                    Text(
                        groupTitle(group.group),
                        fontWeight = FontWeight.Bold,
                    )
                    group.entries.forEach { entry ->
                        Text(entry.title)
                        if (entry.detail.isNotBlank()) {
                            Text(entry.detail)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun groupTitle(group: SearchGroup): String = stringResource(
    when (group) {
        SearchGroup.CUSTOMERS -> R.string.data_search_group_customers
        SearchGroup.ITEMS -> R.string.data_search_group_items
        SearchGroup.INVOICES -> R.string.data_search_group_invoices
        SearchGroup.ORDERS -> R.string.data_search_group_orders
        SearchGroup.PAYMENTS -> R.string.data_search_group_payments
    },
)
