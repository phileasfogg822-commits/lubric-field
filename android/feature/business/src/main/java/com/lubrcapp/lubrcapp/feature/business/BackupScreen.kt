package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.domain.business.RestorePreview
import com.lubrcapp.lubrcapp.domain.business.RestoreRules

/**
 * Phase 28.7 — the backup screen (§127 wizard, minus
 * file picking). It shows exactly what 28.5's
 * [RestorePreview] carries — date, business name,
 * per-table counts, total, verification problems —
 * and gates Restore on 28.5's rule: valid AND
 * explicitly confirmed. The confirm row is a
 * clickable row with a display-only checkbox (the
 * bare-checkbox-beside-text lesson: taps on the
 * label must toggle, so the row owns the click).
 * The Restore button renders only when the shell
 * supplies an apply action; 28.7 supplies none —
 * applying tables through the repository is a
 * shell-layer job, and a button that pretends to
 * restore would be worse than an absent one.
 */
@Composable
fun BackupScreen(
    preview: RestorePreview,
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onRestore: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.data_backup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(preview.backupDate)
        Text(preview.businessName)
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                preview.recordCounts.forEach { (table, count) ->
                    Text(stringResource(R.string.data_backup_count, table, count))
                }
                Text(stringResource(R.string.data_backup_total, preview.totalRecords))
                preview.problems.forEach { problem ->
                    Text(problem)
                }
            }
        }
        Row(modifier = Modifier.clickable { onConfirmedChange(!confirmed) }) {
            Checkbox(checked = confirmed, onCheckedChange = null)
            Text(stringResource(R.string.data_backup_confirm))
        }
        if (onRestore != null) {
            Button(
                onClick = onRestore,
                enabled = RestoreRules.canRestore(preview, confirmed),
            ) {
                Text(stringResource(R.string.data_backup_restore))
            }
        }
    }
}
