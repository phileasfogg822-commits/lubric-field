package com.lubrcapp.lubrcapp.feature.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Phase 28.7 — the export screen (§86, §169). It
 * renders CSV text 28.2's writer produced from
 * real records; the shell owns the generation.
 * The Share button follows the 25.6 nullable
 * callback pattern: it renders only when the
 * shell supplies an action. 28.7 supplies none —
 * sharing a CSV rides 25.7's share helpers as a
 * named follow-up, and a button that goes
 * nowhere would be worse than an absent one.
 */
@Composable
fun ExportScreen(
    csvText: String,
    onShare: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.data_export_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (onShare != null) {
            Button(onClick = onShare) { Text(stringResource(R.string.data_export_share)) }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(csvText)
            }
        }
    }
}
