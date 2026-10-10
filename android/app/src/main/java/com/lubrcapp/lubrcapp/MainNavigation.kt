package com.lubrcapp.lubrcapp

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * App entry routing.
 *
 * Phase 9 restores and validates the current tenant/business context from
 * persisted local state. Business switching is membership-scoped and does not
 * rely on role names or unscoped business queries.
 */
@Composable
fun MainNavigation(
    appName: String,
    businessSetupViewModel: BusinessSetupViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    val setupState by businessSetupViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    var showAccessControl by remember { mutableStateOf(false) }

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
            if (showAccessControl && currentContext != null) {
                AccessControlScreen(
                    context = currentContext,
                    onBack = { showAccessControl = false },
                )
            } else {
                DashboardScreen(
                    state = dashboardState,
                    onSwitchBusiness = dashboardViewModel::switchTo,
                    onOpenAccessControl = { showAccessControl = true },
                )
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
