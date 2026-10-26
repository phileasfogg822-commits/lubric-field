package com.lubrcapp.lubrcapp.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.business.BackupSnapshotOps
import com.lubrcapp.lubrcapp.domain.business.GlobalSearch
import com.lubrcapp.lubrcapp.domain.business.RestoreRules
import com.lubrcapp.lubrcapp.feature.business.BackupScreen
import com.lubrcapp.lubrcapp.feature.business.ExportScreen
import com.lubrcapp.lubrcapp.feature.business.SearchScreen
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.time.LocalDate

/**
 * Phase 28.7 — the data-tools module's shell
 * content: search, export and backup sections over
 * real stored records. Search maps documents,
 * items and parties to 28.1 entries and runs
 * [GlobalSearch] on the typed query. Export renders
 * 28.2 CSV over the stored documents; its Share
 * action is a named follow-up riding 25.7's share
 * helpers, so no share callback is passed. Backup
 * shows 28.5's preview over a snapshot of the
 * stored documents; the business name has no
 * shell surface yet, so the business id stands in
 * (a documented follow-up, not a fabricated
 * name), and no restore action is passed —
 * applying tables is a shell-layer job 28.5 named.
 */
private enum class DataSection {
    SEARCH,
    EXPORT,
    BACKUP,
}

@Composable
fun DataToolsContent(scope: OperationalScope, onBack: () -> Unit) {
    val salesViewModel: SalesViewModel = hiltViewModel()
    val documents by salesViewModel.observeDocuments(scope).collectAsState(initial = emptyList())
    val parties by salesViewModel.observeParties(scope).collectAsState(initial = emptyList())
    val items by salesViewModel.observeItems(scope).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(DataSection.SEARCH) }
    var confirmed by remember { mutableStateOf(false) }
    val entries = remember(documents, parties, items) {
        DataSearchMapper.documentsToEntries(documents) +
            DataSearchMapper.itemsToEntries(items) +
            DataSearchMapper.partiesToEntries(parties)
    }
    val results = remember(query, entries) { GlobalSearch.search(entries, query) }
    val snapshot = remember(documents) {
        BackupSnapshotOps.create(
            backupDate = LocalDate.now().toString(),
            businessName = scope.businessId.value,
            tables = mapOf(
                "sales_documents" to documents.map { document ->
                    mapOf(
                        "Number" to document.number,
                        "Date" to document.date,
                        "Status" to document.status,
                    )
                },
            ),
        )
    }
    val preview = remember(snapshot) { RestoreRules.preview(snapshot) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.data_back)) }
            Button(onClick = { section = DataSection.SEARCH }) {
                Text(stringResource(R.string.data_section_search))
            }
            Button(onClick = { section = DataSection.EXPORT }) {
                Text(stringResource(R.string.data_section_export))
            }
            Button(onClick = { section = DataSection.BACKUP }) {
                Text(stringResource(R.string.data_section_backup))
            }
        }
        when (section) {
            DataSection.SEARCH -> SearchScreen(query, results, { query = it })
            DataSection.EXPORT -> ExportScreen(DataSearchMapper.documentsToCsv(documents))
            DataSection.BACKUP -> BackupScreen(preview, confirmed, { confirmed = it })
        }
    }
}
