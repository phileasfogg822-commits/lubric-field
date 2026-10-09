package com.lubrcapp.lubrcapp.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme

@Composable
fun DashboardScreen(
    businessName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LubrcTheme.tokens.contentPadding),
        verticalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding),
    ) {
        Text(
            text = businessName,
            style = MaterialTheme.typography.headlineMedium,
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(LubrcTheme.tokens.contentPadding)) {
                Text(
                    text = "Business is ready",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Your local business data is available offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
