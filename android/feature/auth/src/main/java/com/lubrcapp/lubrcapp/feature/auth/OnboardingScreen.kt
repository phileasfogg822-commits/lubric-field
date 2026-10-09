package com.lubrcapp.lubrcapp.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.foundation.text.KeyboardOptions
import com.lubrcapp.lubrcapp.core.designsystem.AppButton
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme

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
                progress = { progressFor(state.step) },
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

                        state.errorMessage?.let { error ->
                            Text(
                                text = error,
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
                        Text("Back")
                    }
                    AppButton(
                        onClick = onCreateBusiness,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.isSaving) "Creating…" else "Create Business")
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
                StepTitle("Choose your language", "You can change this later.")
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
                    title = "Welcome to $appName",
                    subtitle = "Set up your business once. Core business data is stored locally and works offline.",
                )
                AppButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Get Started")
                }
            }

            BusinessSetupStep.ENTRY -> {
                StepTitle(
                    title = "How do you want to start?",
                    subtitle = "Create a new offline-first business now.",
                )
                AppButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create New Business")
                }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Join Existing Business — requires connected membership service")
                }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restore Backup — available with backup/import workflow")
                }
            }

            BusinessSetupStep.BUSINESS_DETAILS -> {
                StepTitle("Business details", "Start with the essential identity details.")
                OutlinedTextField(
                    value = state.draft.businessName,
                    onValueChange = onBusinessNameChange,
                    label = { Text("Business name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Business phone") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.email,
                    onValueChange = onEmailChange,
                    label = { Text("Business email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.address,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.draft.postalCode,
                    onValueChange = onPostalCodeChange,
                    label = { Text("Postal code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.website,
                    onValueChange = onWebsiteChange,
                    label = { Text("Website") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
            }

            BusinessSetupStep.COUNTRY -> {
                StepTitle("Country", "Country is stored as an ISO country code.")
                SelectionMenu(
                    label = "Country",
                    selectedCode = state.draft.countryCode,
                    options = countries.map { it.code to it.label },
                    onSelected = onCountryChange,
                )
            }

            BusinessSetupStep.CURRENCY -> {
                StepTitle("Currency", "Choose the accounting currency for this business.")
                SelectionMenu(
                    label = "Currency",
                    selectedCode = state.draft.currencyCode,
                    options = currencies.map { it.code to it.label },
                    onSelected = onCurrencyChange,
                )
            }

            BusinessSetupStep.TAX -> {
                StepTitle("Tax", "Enable tax only if this business uses it.")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.draft.taxEnabled,
                        onCheckedChange = onTaxEnabledChange,
                    )
                    Text("Tax enabled")
                }
                if (state.draft.taxEnabled) {
                    OutlinedTextField(
                        value = state.draft.taxNumber,
                        onValueChange = onTaxNumberChange,
                        label = { Text("Tax registration / number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            BusinessSetupStep.BUSINESS_TYPE -> {
                StepTitle(
                    "Business type",
                    "Use the terminology that fits this deployment; this is not tied to one industry.",
                )
                OutlinedTextField(
                    value = state.draft.businessType,
                    onValueChange = onBusinessTypeChange,
                    label = { Text("Business type") },
                    placeholder = { Text("e.g. wholesale, retail, services") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            BusinessSetupStep.OWNER -> {
                StepTitle("Owner details", "Create the local owner identity for this business.")
                OutlinedTextField(
                    value = state.draft.ownerName,
                    onValueChange = onOwnerNameChange,
                    label = { Text("Owner name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.ownerPhone,
                    onValueChange = onOwnerPhoneChange,
                    label = { Text("Owner phone") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.ownerEmail,
                    onValueChange = onOwnerEmailChange,
                    label = { Text("Owner email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
            }

            BusinessSetupStep.REVIEW -> {
                StepTitle(
                    "Review and create",
                    "Everything below will be committed locally in one transaction.",
                )
                ReviewCard("Business", state.draft.businessName)
                ReviewCard("Country", state.draft.countryCode)
                ReviewCard("Currency", state.draft.currencyCode)
                ReviewCard("Business type", state.draft.businessType)
                ReviewCard("Owner", state.draft.ownerName)
                ReviewCard("Language", state.draft.languageCode)
                ReviewCard("Tax", if (state.draft.taxEnabled) "Enabled" else "Disabled")
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
                        "Select $label"
                    } else {
                        "$selectedCode — ${selectedLabel.orEmpty()}"
                    }
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (code, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text("$code — ${optionLabel}") },
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
                    Text("Back")
                }
            }
            AppButton(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text("Continue")
            }
        }
    }

    private fun progressFor(step: BusinessSetupStep): Float =
        (step.ordinal + 1).toFloat() / BusinessSetupStep.entries.size.toFloat()
}
