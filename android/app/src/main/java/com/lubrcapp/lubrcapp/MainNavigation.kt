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
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.feature.auth.BusinessBootstrapState
import com.lubrcapp.lubrcapp.feature.auth.BusinessSetupViewModel
import com.lubrcapp.lubrcapp.feature.auth.OnboardingScreen
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardScreen
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardViewModel
import com.lubrcapp.lubrcapp.feature.settings.AccessControlScreen
import com.lubrcapp.lubrcapp.shell.AppModuleRegistry
import com.lubrcapp.lubrcapp.shell.BottomNavSelection
import com.lubrcapp.lubrcapp.shell.ShellBottomBar
import androidx.compose.material3.Scaffold
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
 * an unresolved configuration keeps every module visible. Phase 13.3
 * composes the second gate: the current user's granted capabilities (via
 * [ShellAccessViewModel]) hide modules the user may not view, and the
 * same composed visibility guards feature dispatch, so a module hidden
 * by either gate never renders its surface. Phase 13.4 puts the ready
 * surface in a Scaffold: the registry-driven bottom navigation (Home /
 * Items / Parties / More) with the full module list under More; the
 * spec's FAB "+" waits for Package 13.5's quick actions.
 */
@Composable
fun MainNavigation(
    appName: String,
    businessSetupViewModel: BusinessSetupViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    scopeViewModel: OperationalScopeViewModel = hiltViewModel(),
    shellConfigViewModel: ShellConfigViewModel = hiltViewModel(),
    shellAccessViewModel: ShellAccessViewModel = hiltViewModel(),
) {
    val setupState by businessSetupViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    var showAccessControl by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<String?>(null) }
    // "More" is a shell surface (the module-list overflow), not a registry
    // module: it is a plain boolean beside selectedFeature.
    var showMore by remember { mutableStateOf(false) }
    // Keep Android system Back consistent with each feature's visible Back button.
    BackHandler(enabled = selectedFeature != null || showAccessControl || showMore) {
        when {
            selectedFeature != null -> selectedFeature = null
            showAccessControl -> showAccessControl = false
            showMore -> showMore = false
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
            // Live capability gating: re-resolves whenever the current
            // business (and therefore the current user's membership in
            // it) changes. Access control is deny-by-default, so the
            // state starts empty — until the authorization snapshot for
            // this business resolves, no capability-gated module shows;
            // a resolved denial keeps it that way. The previous
            // business's grants are never carried across a switch.
            val grantedCapabilities by produceState<Set<CapabilityKey>>(
                initialValue = emptySet(),
                key1 = currentContext,
            ) {
                value = emptySet()
                if (currentContext != null) {
                    shellAccessViewModel.grantedCapabilities(currentContext)
                        .collect { grants ->
                            value = grants
                        }
                }
            }
            // The one composed visibility the shell renders and dispatches
            // by: configuration gate AND capability gate.
            val visibleModules =
                AppModuleRegistry.visibleModules(enabledModuleKeys, grantedCapabilities)
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
                    // Dispatch honors the same composed visibility as the
                    // module list: a selection whose module is hidden by
                    // configuration or by capability — including one left
                    // open while its grant was revoked — renders no
                    // surface, exactly like an unknown id below.
                    val selectedModule = AppModuleRegistry.module(selectedFeature)
                    val content = selectedModule
                        ?.takeIf {
                            AppModuleRegistry.isVisible(
                                it,
                                enabledModuleKeys,
                                grantedCapabilities,
                            )
                        }
                        ?.content
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
                // Phase 13.4: the ready surface is a Scaffold whose bottom
                // bar is the registry-driven NavigationBar (Home / Items /
                // Parties / More); "More" swaps the dashboard for the full
                // module list. Bar taps set the same selectedFeature the
                // list sets, so the dispatch branch above guards them with
                // the same composed visibility.
                val bottomNavModules =
                    AppModuleRegistry.bottomNavModules(
                        enabledModuleKeys,
                        grantedCapabilities,
                    )
                Scaffold(
                    bottomBar = {
                        ShellBottomBar(
                            bottomNavModules = bottomNavModules,
                            selected =
                                if (showMore) BottomNavSelection.MORE
                                else BottomNavSelection.HOME,
                            onSelectHome = {
                                showMore = false
                                selectedFeature = null
                            },
                            onSelectModule = { module ->
                                showMore = false
                                selectedFeature = module.id
                            },
                            onSelectMore = {
                                showMore = true
                                selectedFeature = null
                            },
                        )
                    },
                ) { padding ->
                    if (showMore) {
                        Column(
                            modifier =
                                Modifier.fillMaxSize()
                                    .padding(padding)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleModules.forEach { module ->
                                Button(
                                    onClick = { selectedFeature = module.id },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) { Text(stringResource(module.titleRes)) }
                            }
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier.fillMaxSize()
                                    .padding(padding)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DashboardScreen(
                                state = dashboardState,
                                onSwitchBusiness = { tenant, business ->
                                    selectedFeature = null
                                    showMore = false
                                    dashboardViewModel.switchTo(tenant, business)
                                },
                                onOpenAccessControl = { showAccessControl = true },
                                modifier = Modifier.weight(1f, fill = false),
                            )
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
