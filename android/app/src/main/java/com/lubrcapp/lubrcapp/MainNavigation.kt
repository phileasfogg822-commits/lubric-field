package com.lubrcapp.lubrcapp

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.core.ui.R as CoreUiR
import com.lubrcapp.lubrcapp.feature.auth.BusinessBootstrapState
import com.lubrcapp.lubrcapp.feature.auth.BusinessSetupViewModel
import com.lubrcapp.lubrcapp.feature.auth.OnboardingScreen
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardScreen
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardViewModel
import com.lubrcapp.lubrcapp.feature.settings.AccessControlScreen
import com.lubrcapp.lubrcapp.shell.AppModuleRegistry
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * App entry routing.
 *
 * Phase 9 restores and validates the current tenant/business context from
 * persisted local state. Business switching is membership-scoped and does not
 * rely on role names or unscoped business queries.
 *
 * Phase 13 routes feature modules through [AppModuleRegistry]: the module
 * list and the feature dispatch both read the registry instead of a
 * hardcoded table. Phase 13.2 gates that list live: the resolved
 * white-label configuration for the current business (via
 * [ShellConfigViewModel]) decides which gated modules are visible, while
 * an unresolved configuration keeps every module visible.
 */
@Composable
fun MainNavigation(
    appName: String,
    businessSetupViewModel: BusinessSetupViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    scopeViewModel: OperationalScopeViewModel = hiltViewModel(),
    shellConfigViewModel: ShellConfigViewModel = hiltViewModel(),
) {
    val setupState by businessSetupViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    var showAccessControl by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<String?>(null) }
    // Keep Android system Back consistent with each feature's visible Back button.
    BackHandler(enabled = selectedFeature != null || showAccessControl) {
        when {
            selectedFeature != null -> selectedFeature = null
            showAccessControl -> showAccessControl = false
        }
    }

    val languageCode = when (val bootstrap = setupState.bootstrapState) {
        is BusinessBootstrapState.Ready -> {
            dashboardState.currentBusiness
                ?.languageCode
                ?.takeIf { it.isNotBlank() }
                ?: bootstrap.bootstrap.languageCode
        }
        else -> setupState.draft.languageCode
    }
    ApplyApplicationLocale(languageCode)

    when (val bootstrap = setupState.bootstrapState) {
        BusinessBootstrapState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        BusinessBootstrapState.NeedsSetup -> {
            OnboardingScreen.Content(
                appName = appName,
                state = setupState,
                languages = businessSetupViewModel.languages,
                countries = businessSetupViewModel.countries,
                currencies = businessSetupViewModel.currencies,
                onSelectLanguage = businessSetupViewModel::selectLanguage,
                onBusinessNameChange = businessSetupViewModel::updateBusinessName,
                onPhoneChange = businessSetupViewModel::updatePhone,
                onEmailChange = businessSetupViewModel::updateEmail,
                onAddressChange = businessSetupViewModel::updateAddress,
                onPostalCodeChange = businessSetupViewModel::updatePostalCode,
                onWebsiteChange = businessSetupViewModel::updateWebsite,
                onCountryChange = businessSetupViewModel::selectCountry,
                onCurrencyChange = businessSetupViewModel::selectCurrency,
                onTaxEnabledChange = businessSetupViewModel::updateTaxEnabled,
                onTaxNumberChange = businessSetupViewModel::updateTaxNumber,
                onBusinessTypeChange = businessSetupViewModel::updateBusinessType,
                onOwnerNameChange = businessSetupViewModel::updateOwnerName,
                onOwnerPhoneChange = businessSetupViewModel::updateOwnerPhone,
                onOwnerEmailChange = businessSetupViewModel::updateOwnerEmail,
                onBack = businessSetupViewModel::back,
                onNext = businessSetupViewModel::next,
                onCreateBusiness = businessSetupViewModel::createBusiness,
            )
        }

        is BusinessBootstrapState.Ready -> {
            val initial = bootstrap.bootstrap
            LaunchedEffect(
                initial.ownerUserId,
                initial.tenantId,
                initial.businessId,
            ) {
                dashboardViewModel.start(
                    userId = initial.ownerUserId,
                    fallbackTenantId = initial.tenantId,
                    fallbackBusinessId = initial.businessId,
                )
            }

            val currentContext = dashboardState.currentBusiness?.context
            val operationalScope by produceState<com.lubrcapp.lubrcapp.core.repository.OperationalScope?>(
                initialValue = null,
                key1 = currentContext,
            ) {
                value = null
                if (currentContext != null) {
                    value = runCatching { scopeViewModel.resolve(currentContext) }.getOrNull()
                }
            }
            // Live configuration gating: re-resolves whenever the current
            // business changes. Null (no context yet, resolution pending,
            // or resolution failed) means no gating — every module stays
            // visible, exactly as before Phase 13.2.
            val enabledModuleKeys by produceState<Set<String>?>(
                initialValue = null,
                key1 = currentContext,
            ) {
                value = null
                if (currentContext != null) {
                    shellConfigViewModel.enabledModuleKeys(currentContext).collect { keys ->
                        value = keys
                    }
                }
            }
            if (showAccessControl && currentContext != null) {
                AccessControlScreen(
                    context = currentContext,
                    onBack = { showAccessControl = false },
                )
            } else if (selectedFeature != null && currentContext != null) {
                val scope = operationalScope
                if (scope == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val back = { selectedFeature = null }
                    val content = AppModuleRegistry.module(selectedFeature)?.content
                    if (content != null) {
                        content(scope, back)
                    } else {
                        // Defensive: selections are only produced by the
                        // registry-driven module list below, so an unknown
                        // id cannot occur; render an empty surface and let
                        // Back return to the dashboard.
                        Box(Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DashboardScreen(
                        state = dashboardState,
                        onSwitchBusiness = { tenant, business ->
                            selectedFeature = null
                            dashboardViewModel.switchTo(tenant, business)
                        },
                        onOpenAccessControl = { showAccessControl = true },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (currentContext != null) {
                        AppModuleRegistry.visibleModules(enabledModuleKeys).forEach { module ->
                            Button(
                                onClick = { selectedFeature = module.id },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) { Text(stringResource(module.titleRes)) }
                        }
                    }
                }
            }
        }

        BusinessBootstrapState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CoreUiR.string.common_business_bootstrap_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ApplyApplicationLocale(languageCode: String) {
    LaunchedEffect(languageCode) {
        if (languageCode.isBlank()) return@LaunchedEffect
        val desired = LocaleListCompat.forLanguageTags(languageCode)
        if (
            AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
            desired.toLanguageTags()
        ) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
