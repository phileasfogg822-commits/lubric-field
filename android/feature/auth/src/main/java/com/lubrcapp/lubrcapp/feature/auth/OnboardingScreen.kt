package com.lubrcapp.lubrcapp.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import com.lubrcapp.lubrcapp.core.designsystem.AppButton
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme
import com.lubrcapp.lubrcapp.feature.auth.R

object OnboardingScreen {

    @Composable
    fun Content(
        appName: String,
        state: BusinessSetupUiState,
        languages: List<LanguageOption>,
        countries: List<CountryOption>,
        currencies: List<CurrencyOption>,
        onSelectLanguage: (String) -> Unit,
        onBusinessNameChange: (String) -> Unit,
        onPhoneChange: (String) -> Unit,
        onEmailChange: (String) -> Unit,
        onAddressChange: (String) -> Unit,
        onPostalCodeChange: (String) -> Unit,
        onWebsiteChange: (String) -> Unit,
        onCountryChange: (String) -> Unit,
        onCurrencyChange: (String) -> Unit,
        onTaxEnabledChange: (Boolean) -> Unit,
        onTaxNumberChange: (String) -> Unit,
        onBusinessTypeChange: (String) -> Unit,
        onOwnerNameChange: (String) -> Unit,
        onOwnerPhoneChange: (String) -> Unit,
        onOwnerEmailChange: (String) -> Unit,
        onBack: () -> Unit,
        onNext: () -> Unit,
        onCreateBusiness: () -> Unit,
    ) {
        val spacing = LubrcTheme.tokens.contentPadding

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            LinearProgressIndicator(
                progress = progressFor(state.step),
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        StepContent(
                            appName = appName,
                            state = state,
                            languages = languages,
                            countries = countries,
                            currencies = currencies,
                            onSelectLanguage = onSelectLanguage,
                            onBusinessNameChange = onBusinessNameChange,
                            onPhoneChange = onPhoneChange,
                            onEmailChange = onEmailChange,
                            onAddressChange = onAddressChange,
                            onPostalCodeChange = onPostalCodeChange,
                            onWebsiteChange = onWebsiteChange,
                            onCountryChange = onCountryChange,
                            onCurrencyChange = onCurrencyChange,
                            onTaxEnabledChange = onTaxEnabledChange,
                            onTaxNumberChange = onTaxNumberChange,
                            onBusinessTypeChange = onBusinessTypeChange,
                            onOwnerNameChange = onOwnerNameChange,
                            onOwnerPhoneChange = onOwnerPhoneChange,
                            onOwnerEmailChange = onOwnerEmailChange,
                            onNext = onNext,
                        )

                        state.error?.let { error ->
                            Text(
                                text = businessSetupErrorMessage(error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (state.step !in setOf(
                    BusinessSetupStep.WELCOME,
                    BusinessSetupStep.ENTRY,
                    BusinessSetupStep.REVIEW,
                )
            ) {
                NavigationRow(
                    showBack = state.step != BusinessSetupStep.LANGUAGE,
                    onBack = onBack,
                    onNext = onNext,
                )
            } else if (state.step == BusinessSetupStep.REVIEW) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                    AppButton(
                        onClick = onCreateBusiness,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.isSaving) stringResource(R.string.onboarding_creating) else stringResource(R.string.onboarding_create_business))
                    }
                }
            }
        }
    }

    @Composable
    private fun StepContent(
        appName: String,
        state: BusinessSetupUiState,
        languages: List<LanguageOption>,
        countries: List<CountryOption>,
        currencies: List<CurrencyOption>,
        onSelectLanguage: (String) -> Unit,
        onBusinessNameChange: (String) -> Unit,
        onPhoneChange: (String) -> Unit,
        onEmailChange: (String) -> Unit,
        onAddressChange: (String) -> Unit,
        onPostalCodeChange: (String) -> Unit,
        onWebsiteChange: (String) -> Unit,
        onCountryChange: (String) -> Unit,
        onCurrencyChange: (String) -> Unit,
        onTaxEnabledChange: (Boolean) -> Unit,
        onTaxNumberChange: (String) -> Unit,
        onBusinessTypeChange: (String) -> Unit,
        onOwnerNameChange: (String) -> Unit,
        onOwnerPhoneChange: (String) -> Unit,
        onOwnerEmailChange: (String) -> Unit,
        onNext: () -> Unit,
    ) {
        when (state.step) {
            BusinessSetupStep.LANGUAGE -> {
                StepTitle(stringResource(R.string.onboarding_choose_language), stringResource(R.string.onboarding_change_language_later))
                languages.forEach { language ->
                    OutlinedButton(
                        onClick = { onSelectLanguage(language.code) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val selected = state.draft.languageCode == language.code
                        Text(if (selected) "✓ ${language.label}" else language.label)
                    }
                }
            }

            BusinessSetupStep.WELCOME -> {
                StepTitle(
                    title = stringResource(R.string.onboarding_welcome, appName),
                    subtitle = stringResource(R.string.onboarding_offline_setup),
                )
                AppButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }

            BusinessSetupStep.ENTRY -> {
                StepTitle(
                    title = stringResource(R.string.onboarding_start_question),
                    subtitle = stringResource(R.string.onboarding_create_offline),
                )
                AppButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_create_new_business))
                }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_join_unavailable))
                }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_restore_unavailable))
                }
            }

            BusinessSetupStep.BUSINESS_DETAILS -> {
                StepTitle(stringResource(R.string.onboarding_business_details), stringResource(R.string.onboarding_business_details_hint))
                OutlinedTextField(
                    value = state.draft.businessName,
                    onValueChange = onBusinessNameChange,
                    label = { Text(stringResource(R.string.business_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.phone,
                    onValueChange = onPhoneChange,
                    label = { Text(stringResource(R.string.business_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.business_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.address)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.draft.postalCode,
                    onValueChange = onPostalCodeChange,
                    label = { Text(stringResource(R.string.postal_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.website,
                    onValueChange = onWebsiteChange,
                    label = { Text(stringResource(R.string.website)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
            }

            BusinessSetupStep.COUNTRY -> {
                StepTitle(stringResource(R.string.country), stringResource(R.string.country_hint))
                SelectionMenu(
                    label = stringResource(R.string.country),
                    selectedCode = state.draft.countryCode,
                    options = countries.map { it.code to it.label },
                    onSelected = onCountryChange,
                )
            }

            BusinessSetupStep.CURRENCY -> {
                StepTitle(stringResource(R.string.currency), stringResource(R.string.currency_hint))
                SelectionMenu(
                    label = stringResource(R.string.currency),
                    selectedCode = state.draft.currencyCode,
                    options = currencies.map { it.code to it.label },
                    onSelected = onCurrencyChange,
                )
            }

            BusinessSetupStep.TAX -> {
                StepTitle(stringResource(R.string.tax), stringResource(R.string.tax_hint))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.draft.taxEnabled,
                        onCheckedChange = onTaxEnabledChange,
                    )
                    Text(stringResource(R.string.tax_enabled))
                }
                if (state.draft.taxEnabled) {
                    OutlinedTextField(
                        value = state.draft.taxNumber,
                        onValueChange = onTaxNumberChange,
                        label = { Text(stringResource(R.string.tax_registration)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            BusinessSetupStep.BUSINESS_TYPE -> {
                StepTitle(
                    stringResource(R.string.business_type),
                    stringResource(R.string.business_type_hint),
                )
                OutlinedTextField(
                    value = state.draft.businessType,
                    onValueChange = onBusinessTypeChange,
                    label = { Text(stringResource(R.string.business_type)) },
                    placeholder = { Text(stringResource(R.string.business_type_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            BusinessSetupStep.OWNER -> {
                StepTitle(stringResource(R.string.owner_details), stringResource(R.string.owner_details_hint))
                OutlinedTextField(
                    value = state.draft.ownerName,
                    onValueChange = onOwnerNameChange,
                    label = { Text(stringResource(R.string.owner_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.ownerPhone,
                    onValueChange = onOwnerPhoneChange,
                    label = { Text(stringResource(R.string.owner_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.ownerEmail,
                    onValueChange = onOwnerEmailChange,
                    label = { Text(stringResource(R.string.owner_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
            }

            BusinessSetupStep.REVIEW -> {
                StepTitle(
                    stringResource(R.string.review_create),
                    stringResource(R.string.review_create_hint),
                )
                ReviewCard(stringResource(R.string.review_business), state.draft.businessName)
                ReviewCard(stringResource(R.string.country), state.draft.countryCode)
                ReviewCard(stringResource(R.string.currency), state.draft.currencyCode)
                ReviewCard(stringResource(R.string.business_type), state.draft.businessType)
                ReviewCard(stringResource(R.string.owner_details), state.draft.ownerName)
                ReviewCard(stringResource(R.string.language), state.draft.languageCode)
                ReviewCard(stringResource(R.string.tax), if (state.draft.taxEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled))
            }
        }
    }

    @Composable
    private fun StepTitle(
        title: String,
        subtitle: String,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun SelectionMenu(
        label: String,
        selectedCode: String,
        options: List<Pair<String, String>>,
        onSelected: (String) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = options.firstOrNull { it.first == selectedCode }?.second

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (selectedCode.isBlank()) {
                        stringResource(R.string.select_value, label)
                    } else {
                        stringResource(R.string.selection_value, selectedCode, selectedLabel.orEmpty())
                    }
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (code, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.selection_value, code, optionLabel)) },
                        onClick = {
                            onSelected(code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReviewCard(
        label: String,
        value: String,
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(LubrcTheme.tokens.contentPadding)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    @Composable
    private fun NavigationRow(
        showBack: Boolean,
        onBack: () -> Unit,
        onNext: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding),
        ) {
            if (showBack) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            AppButton(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.continue_label))
            }
        }
    }

    @Composable
    private fun businessSetupErrorMessage(error: BusinessSetupError): String =
        stringResource(
            when (error) {
                BusinessSetupError.LANGUAGE_REQUIRED -> R.string.setup_error_language
                BusinessSetupError.BUSINESS_NAME_REQUIRED -> R.string.setup_error_business_name
                BusinessSetupError.COUNTRY_REQUIRED -> R.string.setup_error_country
                BusinessSetupError.CURRENCY_REQUIRED -> R.string.setup_error_currency
                BusinessSetupError.BUSINESS_TYPE_REQUIRED -> R.string.setup_error_business_type
                BusinessSetupError.OWNER_NAME_REQUIRED -> R.string.setup_error_owner_name
                BusinessSetupError.CREATE_FAILED -> R.string.setup_error_create
            }
        )

    private fun progressFor(step: BusinessSetupStep): Float =
        (step.ordinal + 1).toFloat() / BusinessSetupStep.entries.size.toFloat()
}
