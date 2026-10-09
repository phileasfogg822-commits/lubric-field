package com.lubrcapp.lubrcapp.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSwitchBusiness: (TenantId, BusinessId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LubrcTheme.tokens.contentPadding

    if (state.isLoading && state.currentBusiness == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        state.currentBusiness?.let { current ->
            Text(
                text = current.name,
                style = MaterialTheme.typography.headlineMedium,
            )

            if (state.availableBusinesses.size > 1) {
                BusinessSwitcherMenu(
                    state = state,
                    onSwitchBusiness = onSwitchBusiness,
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Text(
                        text = "Current business",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = current.businessType,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = current.currency.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tenant " + current.context.tenantId.value.take(8) + "…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } ?: Text(
            text = "No accessible business is available for this user.",
            style = MaterialTheme.typography.bodyLarge,
        )

        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BusinessSwitcherMenu(
    state: DashboardUiState,
    onSwitchBusiness: (TenantId, BusinessId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.currentBusiness ?: return

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !state.isSwitching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isSwitching) {
                    "Switching…"
                } else {
                    "Switch business"
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.availableBusinesses.forEach { business ->
                val selected = business.context == current.context
                DropdownMenuItem(
                    text = {
                        Text(
                            if (selected) {
                                "✓ " + business.name
                            } else {
                                business.name
                            }
                        )
                    },
                    enabled = !selected && !state.isSwitching,
                    onClick = {
                        expanded = false
                        onSwitchBusiness(
                            business.context.tenantId,
                            business.context.businessId,
                        )
                    },
                )
            }
        }
    }
}
