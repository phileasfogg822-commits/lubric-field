package com.lubrcapp.lubrcapp.feature.printing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Phase 32.5 — the themed print preview: the same themed rows the
 * PDF writer renders, plus the branding header (badge + big name)
 * and the Vyapar-style theme-dot picker. The old [PrintPreviewScreen]
 * stays for the POS receipt path; this screen is the invoice and
 * statement path. When [onThemeChange] is null the picker row is
 * hidden and the screen is a fixed-theme preview.
 */
@Composable
fun ThemedPrintPreviewScreen(
    headerName: String,
    headerDetail: String?,
    photo: ImageBitmap?,
    title: String,
    theme: PrintTheme,
    onThemeChange: ((PrintTheme) -> Unit)?,
    rows: List<ThemedRow>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
) {
    val themeColor = Color(theme.argb)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onBack) { Text(stringResource(R.string.print_preview_back)) }
        Text(
            stringResource(R.string.print_preview_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (onThemeChange != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.print_preview_theme))
                PRINT_THEMES.forEach { candidate ->
                    val selected = candidate.id == theme.id
                    val dotName = stringResource(candidate.nameRes)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(candidate.argb))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) Color.Black else Color.Transparent,
                                shape = CircleShape,
                            )
                            .semantics { contentDescription = dotName }
                            .clickable { onThemeChange(candidate) },
                    ) {
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(themeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initialsFor(headerName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = headerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = themeColor,
                )
                if (headerDetail != null) {
                    Text(text = headerDetail, style = MaterialTheme.typography.bodyMedium)
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShare) { Text(stringResource(R.string.print_preview_share)) }
            if (onDownload != null) {
                Button(onClick = onDownload) { Text(stringResource(R.string.print_preview_download)) }
            }
            if (onPrint != null) {
                Button(onClick = onPrint) { Text(stringResource(R.string.print_preview_print)) }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rows.forEach { row ->
                    when (row) {
                        is ThemedRow.Meta -> Text(text = row.text)
                        is ThemedRow.Blank -> Spacer(modifier = Modifier.height(12.dp))
                        is ThemedRow.TableHeader -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(themeColor).padding(vertical = 4.dp),
                            ) {
                                row.columns.forEachIndexed { i, col ->
                                    Text(
                                        text = col,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(row.weights[i]).padding(horizontal = 6.dp),
                                    )
                                }
                            }
                        }
                        is ThemedRow.TableRow -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.cells.forEachIndexed { i, cell ->
                                    Text(
                                        text = cell,
                                        textAlign = if (i >= row.numericFrom) TextAlign.End else TextAlign.Start,
                                        modifier = Modifier.weight(row.weights[i]).padding(horizontal = 6.dp),
                                    )
                                }
                            }
                        }
                        is ThemedRow.Total -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${row.label}: ${row.value}",
                                    fontWeight = if (row.emphasized) FontWeight.Bold else FontWeight.Normal,
                                    color = if (row.emphasized) themeColor else Color.Unspecified,
                                )
                            }
                        }
                        is ThemedRow.Note -> Text(text = row.text)
                    }
                }
            }
        }
    }
}
