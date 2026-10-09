package com.lubrcapp.lubrcapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateInitialBusinessUseCase
import com.lubrcapp.lubrcapp.core.repository.usecase.ObserveBusinessBootstrapUseCase
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class BusinessSetupStep {
    LANGUAGE,
    WELCOME,
    ENTRY,
    BUSINESS_DETAILS,
    COUNTRY,
    CURRENCY,
    TAX,
    BUSINESS_TYPE,
    OWNER,
    REVIEW,
}

data class LanguageOption(
    val code: String,
    val label: String,
)

data class CountryOption(
    val code: String,
    val label: String,
)

data class CurrencyOption(
    val code: String,
    val label: String,
)

data class BusinessSetupDraft(
    val languageCode: String = "en",
    val businessName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val countryCode: String = "",
    val postalCode: String = "",
    val website: String = "",
    val currencyCode: String = "",
    val taxEnabled: Boolean = false,
    val taxNumber: String = "",
    val businessType: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val ownerEmail: String = "",
)

sealed interface BusinessBootstrapState {
    data object Loading : BusinessBootstrapState
    data object NeedsSetup : BusinessBootstrapState
    data class Ready(val bootstrap: BusinessBootstrap) : BusinessBootstrapState
    data object Error : BusinessBootstrapState
}

enum class BusinessSetupError {
    LANGUAGE_REQUIRED,
    BUSINESS_NAME_REQUIRED,
    COUNTRY_REQUIRED,
    CURRENCY_REQUIRED,
    BUSINESS_TYPE_REQUIRED,
    OWNER_NAME_REQUIRED,
    CREATE_FAILED,
}

data class BusinessSetupUiState(
    val bootstrapState: BusinessBootstrapState = BusinessBootstrapState.Loading,
    val step: BusinessSetupStep = BusinessSetupStep.LANGUAGE,
    val draft: BusinessSetupDraft = BusinessSetupDraft(),
    val isSaving: Boolean = false,
    val error: BusinessSetupError? = null,
)

@HiltViewModel
class BusinessSetupViewModel @Inject constructor(
    private val observeBootstrap: ObserveBusinessBootstrapUseCase,
    private val createInitialBusiness: CreateInitialBusinessUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(BusinessSetupUiState())
    val state: StateFlow<BusinessSetupUiState> = mutableState.asStateFlow()

    val languages: List<LanguageOption> = listOf(
        LanguageOption("en", "English"),
        LanguageOption("ur", "اردو"),
        LanguageOption("ar", "العربية"),
        LanguageOption("hi", "हिन्दी"),
    )

    val countries: List<CountryOption> = Locale.getAvailableLocales()
        .asSequence()
        .filter { it.country.isNotBlank() }
        .map { locale ->
            CountryOption(
                code = locale.country.uppercase(Locale.ROOT),
                label = locale.getDisplayCountry(Locale.ENGLISH),
            )
        }
        .filter { it.label.isNotBlank() }
        .distinctBy { it.code }
        .sortedBy { it.label }
        .toList()

    val currencies: List<CurrencyOption> = Currency.getAvailableCurrencies()
        .asSequence()
        .map { currency ->
            CurrencyOption(
                code = currency.currencyCode,
                label = "${currency.currencyCode} — ${currency.displayName}",
            )
        }
        .sortedBy { it.code }
        .toList()

    init {
        viewModelScope.launch {
            observeBootstrap()
                .catch { error ->
                    mutableState.value = mutableState.value.copy(
                        bootstrapState = BusinessBootstrapState.Error
                    )
                }
                .collectLatest { bootstrap ->
                    mutableState.value = mutableState.value.copy(
                        bootstrapState = bootstrap?.let(BusinessBootstrapState::Ready)
                            ?: BusinessBootstrapState.NeedsSetup,
                        isSaving = false,
                    )
                }
        }
    }

    fun selectLanguage(code: String) {
        if (languages.none { it.code == code }) return
        updateDraft { copy(languageCode = code) }
    }

    fun updateBusinessName(value: String) = updateDraft { copy(businessName = value) }
    fun updatePhone(value: String) = updateDraft { copy(phone = value) }
    fun updateEmail(value: String) = updateDraft { copy(email = value) }
    fun updateAddress(value: String) = updateDraft { copy(address = value) }
    fun updatePostalCode(value: String) = updateDraft { copy(postalCode = value) }
    fun updateWebsite(value: String) = updateDraft { copy(website = value) }
    fun updateTaxEnabled(value: Boolean) = updateDraft { copy(taxEnabled = value) }
    fun updateTaxNumber(value: String) = updateDraft { copy(taxNumber = value) }
    fun updateBusinessType(value: String) = updateDraft { copy(businessType = value) }
    fun updateOwnerName(value: String) = updateDraft { copy(ownerName = value) }
    fun updateOwnerPhone(value: String) = updateDraft { copy(ownerPhone = value) }
    fun updateOwnerEmail(value: String) = updateDraft { copy(ownerEmail = value) }

    fun selectCountry(code: String) {
        val normalized = code.trim().uppercase(Locale.ROOT)
        if (countries.none { it.code == normalized }) return
        updateDraft { copy(countryCode = normalized) }

        if (mutableState.value.draft.currencyCode.isBlank()) {
            currencyForCountry(normalized)?.let { currencyCode ->
                updateDraft { copy(currencyCode = currencyCode) }
            }
        }
    }

    fun selectCurrency(code: String) {
        val normalized = code.trim().uppercase(Locale.ROOT)
        if (currencies.none { it.code == normalized }) return
        updateDraft { copy(currencyCode = normalized) }
    }

    fun next() {
        val current = mutableState.value
        validateStep(current)?.let { error ->
            mutableState.value = current.copy(error = error)
            return
        }

        val next = when (current.step) {
            BusinessSetupStep.LANGUAGE -> BusinessSetupStep.WELCOME
            BusinessSetupStep.WELCOME -> BusinessSetupStep.ENTRY
            BusinessSetupStep.ENTRY -> BusinessSetupStep.BUSINESS_DETAILS
            BusinessSetupStep.BUSINESS_DETAILS -> BusinessSetupStep.COUNTRY
            BusinessSetupStep.COUNTRY -> BusinessSetupStep.CURRENCY
            BusinessSetupStep.CURRENCY -> BusinessSetupStep.TAX
            BusinessSetupStep.TAX -> BusinessSetupStep.BUSINESS_TYPE
            BusinessSetupStep.BUSINESS_TYPE -> BusinessSetupStep.OWNER
            BusinessSetupStep.OWNER -> BusinessSetupStep.REVIEW
            BusinessSetupStep.REVIEW -> BusinessSetupStep.REVIEW
        }
        mutableState.value = current.copy(step = next, error = null)
    }

    fun back() {
        val current = mutableState.value
        val previous = when (current.step) {
            BusinessSetupStep.LANGUAGE -> BusinessSetupStep.LANGUAGE
            BusinessSetupStep.WELCOME -> BusinessSetupStep.LANGUAGE
            BusinessSetupStep.ENTRY -> BusinessSetupStep.WELCOME
            BusinessSetupStep.BUSINESS_DETAILS -> BusinessSetupStep.ENTRY
            BusinessSetupStep.COUNTRY -> BusinessSetupStep.BUSINESS_DETAILS
            BusinessSetupStep.CURRENCY -> BusinessSetupStep.COUNTRY
            BusinessSetupStep.TAX -> BusinessSetupStep.CURRENCY
            BusinessSetupStep.BUSINESS_TYPE -> BusinessSetupStep.TAX
            BusinessSetupStep.OWNER -> BusinessSetupStep.BUSINESS_TYPE
            BusinessSetupStep.REVIEW -> BusinessSetupStep.OWNER
        }
        mutableState.value = current.copy(step = previous, error = null)
    }

    fun createBusiness() {
        val current = mutableState.value
        if (current.step != BusinessSetupStep.REVIEW || current.isSaving) return

        validateComplete(current.draft)?.let { error ->
            mutableState.value = current.copy(error = error)
            return
        }

        mutableState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val draft = mutableState.value.draft
                createInitialBusiness(
                    CreateInitialBusinessRequest(
                        languageCode = draft.languageCode,
                        businessName = draft.businessName.trim(),
                        businessType = draft.businessType.trim(),
                        phone = draft.phone.trim(),
                        email = draft.email.trim(),
                        address = draft.address.trim(),
                        countryCode = draft.countryCode,
                        postalCode = draft.postalCode.trim(),
                        website = draft.website.trim(),
                        currency = CurrencyCode.of(draft.currencyCode),
                        timezone = ZoneId.systemDefault(),
                        taxEnabled = draft.taxEnabled,
                        taxNumber = draft.taxNumber.trim().takeIf(String::isNotEmpty),
                        ownerName = draft.ownerName.trim(),
                        ownerPhone = draft.ownerPhone.trim(),
                        ownerEmail = draft.ownerEmail.trim().takeIf(String::isNotEmpty),
                    )
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = BusinessSetupError.CREATE_FAILED,
                )
            }
        }
    }

    private fun updateDraft(transform: BusinessSetupDraft.() -> BusinessSetupDraft) {
        val current = mutableState.value
        mutableState.value = current.copy(
            draft = current.draft.transform(),
            error = null,
        )
    }

    private fun validateStep(state: BusinessSetupUiState): BusinessSetupError? =
        when (state.step) {
            BusinessSetupStep.LANGUAGE ->
                if (state.draft.languageCode.isBlank()) BusinessSetupError.LANGUAGE_REQUIRED else null
            BusinessSetupStep.BUSINESS_DETAILS ->
                if (state.draft.businessName.isBlank()) BusinessSetupError.BUSINESS_NAME_REQUIRED else null
            BusinessSetupStep.COUNTRY ->
                if (state.draft.countryCode.isBlank()) BusinessSetupError.COUNTRY_REQUIRED else null
            BusinessSetupStep.CURRENCY ->
                if (state.draft.currencyCode.isBlank()) BusinessSetupError.CURRENCY_REQUIRED else null
            BusinessSetupStep.BUSINESS_TYPE ->
                if (state.draft.businessType.isBlank()) BusinessSetupError.BUSINESS_TYPE_REQUIRED else null
            BusinessSetupStep.OWNER ->
                if (state.draft.ownerName.isBlank()) BusinessSetupError.OWNER_NAME_REQUIRED else null
            else -> null
        }

    private fun validateComplete(draft: BusinessSetupDraft): BusinessSetupError? =
        when {
            draft.languageCode.isBlank() -> BusinessSetupError.LANGUAGE_REQUIRED
            draft.businessName.isBlank() -> BusinessSetupError.BUSINESS_NAME_REQUIRED
            draft.countryCode.isBlank() -> BusinessSetupError.COUNTRY_REQUIRED
            draft.currencyCode.isBlank() -> BusinessSetupError.CURRENCY_REQUIRED
            draft.businessType.isBlank() -> BusinessSetupError.BUSINESS_TYPE_REQUIRED
            draft.ownerName.isBlank() -> BusinessSetupError.OWNER_NAME_REQUIRED
            else -> null
        }

    private fun currencyForCountry(countryCode: String): String? {
        val locale = Locale.getAvailableLocales()
            .firstOrNull { it.country.equals(countryCode, ignoreCase = true) }
            ?: return null
        return runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
    }
}
