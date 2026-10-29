package com.lubrcapp.lubrcapp.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme
import com.lubrcapp.lubrcapp.core.repository.isDemoScopeId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.feature.dashboard.R

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSwitchBusiness: (TenantId, BusinessId) -> Unit,
    onOpenAccessControl: () -> Unit,
    modifier: Modifier = Modifier,
    // Phase 13.5: dashboard cards are configurable (spec section 11).
    // The shell computes card visibility from its dashboard-card registry
    // and passes the result in; the default keeps every existing caller
    // and test rendering the card exactly as before.
    showCurrentBusinessCard: Boolean = true,
    // Phase 32.3: demo companies. The shell passes whether any listed
    // business is a demo and a delete callback; both default to
    // "no demos", so existing callers render exactly as before.
    hasDemoCompanies: Boolean = false,
    onDeleteDemoCompanies: (() -> Unit)? = null,
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

            OutlinedButton(
                onClick = onOpenAccessControl,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dashboard_team_access))
            }

            if (state.availableBusinesses.size > 1) {
                BusinessSwitcherMenu(
                    state = state,
                    onSwitchBusiness = onSwitchBusiness,
                )
            }

            if (hasDemoCompanies && onDeleteDemoCompanies != null) {
                var showDeleteDemoDialog by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showDeleteDemoDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dashboard_delete_demo))
                }
                if (showDeleteDemoDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDemoDialog = false },
                        title = { Text(stringResource(R.string.dashboard_delete_demo_title)) },
                        text = { Text(stringResource(R.string.dashboard_delete_demo_text)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDemoDialog = false
                                    onDeleteDemoCompanies()
                                },
                            ) {
                                Text(stringResource(R.string.dashboard_delete_demo_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDemoDialog = false }) {
                                Text(stringResource(R.string.dashboard_delete_demo_cancel))
                            }
                        },
                    )
                }
            }

            if (showCurrentBusinessCard) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_current_business),
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
                        text = stringResource(R.string.dashboard_tenant_short, current.context.tenantId.value.take(8)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
        } ?: Text(
            text = stringResource(R.string.dashboard_no_business),
            style = MaterialTheme.typography.bodyLarge,
        )

        state.error?.let { error ->
            Text(
                text = dashboardErrorMessage(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun dashboardErrorMessage(error: DashboardError): String =
    stringResource(
        when (error) {
            DashboardError.RESTORE_FAILED -> R.string.dashboard_error_restore
            DashboardError.NO_VALID_CURRENT -> R.string.dashboard_error_no_current
            DashboardError.ACCESS_DENIED -> R.string.dashboard_error_access_denied
            DashboardError.BUSINESS_UNAVAILABLE -> R.string.dashboard_error_unavailable
        }
    )

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
                    stringResource(R.string.dashboard_switching)
                } else {
                    stringResource(R.string.dashboard_switch_business)
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.availableBusinesses.forEach { business ->
                val selected = business.context == current.context
                val isDemo = isDemoScopeId(business.context.businessId.value)
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (selected) {
                                "✓ " + business.name
                            } else {
                                business.name
                            }) + if (isDemo) {
                                " · " + stringResource(R.string.dashboard_demo_badge)
                            } else {
                                ""
                            },
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
