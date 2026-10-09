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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.feature.auth.BusinessBootstrapState
import com.lubrcapp.lubrcapp.feature.auth.BusinessSetupViewModel
import com.lubrcapp.lubrcapp.feature.auth.OnboardingScreen
import com.lubrcapp.lubrcapp.feature.dashboard.DashboardScreen

/**
 * Phase-8 app entry routing.
 *
 * Multi-business switching/current-business context is intentionally deferred
 * to Phase 9. Until then the first active local business is the bootstrap
 * business.
 */
@Composable
fun MainNavigation(
    appName: String,
    businessSetupViewModel: BusinessSetupViewModel = hiltViewModel(),
) {
    val state by businessSetupViewModel.state.collectAsState()

    val languageCode = when (val bootstrap = state.bootstrapState) {
        is BusinessBootstrapState.Ready -> bootstrap.bootstrap.languageCode
        else -> state.draft.languageCode
    }
    ApplyApplicationLocale(languageCode)

    when (val bootstrap = state.bootstrapState) {
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
                state = state,
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
            DashboardScreen(
                businessName = bootstrap.bootstrap.businessName,
            )
        }

        is BusinessBootstrapState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bootstrap.message,
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
