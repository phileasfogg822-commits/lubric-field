package com.lubrcapp.lubrcapp.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.R

/**
 * Phase 13.5 — the two surfaces [QuickActionsRegistry] drives: the
 * quick-actions section on the dashboard (spec §12 "Home Quick Actions")
 * and the bottom sheet behind the shell's floating action button
 * (spec §13 "+"). Both render exactly the actions the registry's
 * composed visibility returns; selecting one hands its target module id
 * to the shell's existing dispatch.
 */
/**
 * Phase 33.2 — the section renders the same actions in the same
 * order, but as a two-column grid of tappable cards instead of a
 * list of full-width buttons, so Home reads as a modern action
 * grid. A lone action in the last row keeps half width.
 */
@Composable
fun QuickActionsSection(
    actions: List<QuickAction>,
    onSelect: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    // Phase 33.5: tablet grid width. Default 2 keeps every existing caller
    // (and the phone layout) exactly as before.
    columns: Int = 2,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_actions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        actions.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { action ->
                    Card(
                        onClick = { onSelect(action) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(action.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                repeat(columns.coerceAtLeast(1) - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    actions: List<QuickAction>,
    onSelect: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            actions.forEach { action ->
                TextButton(
                    onClick = { onSelect(action) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) { Text(stringResource(action.titleRes)) }
            }
        }
    }
}
