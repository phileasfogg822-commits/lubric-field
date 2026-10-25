package com.lubrcapp.lubrcapp.feature.printing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 25.6 — the print preview screen: spec §99's
 * Preview action. It renders a DocumentLayout's
 * lines as text rows — alignment from the line's
 * semantic align, bold from the line's bold flag,
 * size from the line's point size — inside a
 * scrolling Column, with the §99 actions (Share,
 * Download PDF, Print) as buttons in a fixed row
 * above the content. The actions are callbacks
 * the shell wires later (25.7): this package
 * owns the preview UI, not the share sheet, the
 * file write, or the printer.
 *
 * Layout note (20.4 lesson): the scrolling
 * content lives in a weight(1f) Box, so every
 * row is reachable by scroll; the action
 * buttons stay fixed, so tests never scroll
 * for them. A LazyColumn would NOT compose
 * below-fold rows for the tests.
 */
@Composable
fun PrintPreviewScreen(
    document: PrintableDocument,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onPrint: () -> Unit,
) {
    val layout = remember(document) { layoutDocument(document) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.print_preview_back)) }
        Text(
            stringResource(R.string.print_preview_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShare) { Text(stringResource(R.string.print_preview_share)) }
            Button(onClick = onDownload) { Text(stringResource(R.string.print_preview_download)) }
            Button(onClick = onPrint) { Text(stringResource(R.string.print_preview_print)) }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                layout.pages.flatMap { it.lines }.forEach { line ->
                    Text(
                        text = line.text,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = line.size.sp,
                        textAlign = when (line.align) {
                            LayoutAlign.LEFT -> TextAlign.Left
                            LayoutAlign.CENTER -> TextAlign.Center
                            LayoutAlign.RIGHT -> TextAlign.End
                        },
                    )
                }
            }
        }
    }
}
