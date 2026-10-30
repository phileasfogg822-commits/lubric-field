package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Phase 33.4 — one labelled group inside a form: a card with an
 * optional section title and padded, spaced content. The title is
 * a plain String so callers pass an existing stringResource and
 * no new strings are introduced. Like AppCard, this reads theme
 * tokens, so screens that use it must render inside LubrcappTheme
 * (and their compose tests must wrap in it, per the 33.3 lesson).
 */
@Composable
fun FormSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
