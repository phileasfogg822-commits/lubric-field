package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingOverrides

enum class DeploymentEnvironment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    CUSTOM;

    companion object {
        fun parse(value: String): DeploymentEnvironment =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: CUSTOM
    }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun parseOrNull(value: String?): ThemeMode? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            }
    }
}

data class TerminologyConfig(
    val sale: String,
    val invoice: String,
    val taxInvoice: String,
    val cashMemo: String,
    val quotation: String,
    val estimate: String,
    val customer: String,
    val supplier: String,
    val party: String,
    val item: String,
    val category: String,
    val brand: String,
    val unit: String,
    val warehouse: String,
    val paymentMode: String,
    val expenseCategory: String,
    val otherIncome: String,
) {
    init {
        require(
            listOf(
                sale, invoice, taxInvoice, cashMemo, quotation, estimate,
                customer, supplier, party, item, category, brand, unit,
                warehouse, paymentMode, expenseCategory, otherIncome,
            ).all { it.isNotBlank() }
        ) { "Terminology labels cannot be blank" }
    }
}

data class TerminologyOverrides(
    val sale: String? = null,
    val invoice: String? = null,
    val taxInvoice: String? = null,
    val cashMemo: String? = null,
    val quotation: String? = null,
    val estimate: String? = null,
    val customer: String? = null,
    val supplier: String? = null,
    val party: String? = null,
    val item: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val unit: String? = null,
    val warehouse: String? = null,
    val paymentMode: String? = null,
    val expenseCategory: String? = null,
    val otherIncome: String? = null,
)

data class TaxConfig(
    val enabled: Boolean,
    val label: String,
    val numberLabel: String,
    val defaultRate: Percentage,
) {
    init {
        require(label.isNotBlank()) { "Tax label cannot be blank" }
        require(numberLabel.isNotBlank()) { "Tax number label cannot be blank" }
    }
}

data class TaxOverrides(
    val enabled: Boolean? = null,
    val label: String? = null,
    val numberLabel: String? = null,
    val defaultRate: Percentage? = null,
)

data class PaymentMethodConfig(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Payment method id cannot be blank" }
        require(label.isNotBlank()) { "Payment method label cannot be blank" }
    }
}

data class DeploymentConfig(
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val apiEndpoint: String,
    val environment: DeploymentEnvironment,
    val firebaseProjectId: String?,
    val licenseId: String?,
    val includedModules: Set<String>,
    val branding: BrandingConfig,
    val terminology: TerminologyConfig,
    val defaultTax: TaxConfig,
    val defaultPaymentMethods: List<PaymentMethodConfig>,
    val defaultLanguage: String,
    val defaultThemeMode: ThemeMode,
    val supportEmail: String?,
    val supportPhone: String?,
    val termsUrl: String?,
    val privacyUrl: String?,
    val website: String?,
) {
    init {
        require(appName.isNotBlank()) { "Deployment appName cannot be blank" }
        require(applicationId.isNotBlank()) { "Deployment applicationId cannot be blank" }
        require(versionName.isNotBlank()) { "Deployment versionName cannot be blank" }
        require(versionCode > 0) { "Deployment versionCode must be positive" }
        require(apiEndpoint.endsWith("/")) { "apiEndpoint must end with /" }
        require(apiEndpoint.startsWith("https://") || apiEndpoint.startsWith("http://")) {
            "apiEndpoint must use http or https"
        }
        if (environment == DeploymentEnvironment.PRODUCTION) {
            require(apiEndpoint.startsWith("https://")) { "Production apiEndpoint must use https" }
        }
        require(includedModules.none { it.isBlank() }) { "Included module keys cannot be blank" }
        require(defaultLanguage.isNotBlank()) { "defaultLanguage cannot be blank" }
    }
}

data class BusinessConfig(
    val tenantId: TenantId,
    val businessId: BusinessId,
    val businessName: String,
    val currency: CurrencyCode,
    val branding: BrandingOverrides,
    val terminology: TerminologyOverrides,
    val tax: TaxOverrides,
    val paymentMethods: List<PaymentMethodConfig>?,
    val language: String?,
    val themeMode: ThemeMode?,
    val moduleOverrides: Map<String, Boolean>,
)

data class ResolvedWhiteLabelConfig(
    val deployment: DeploymentConfig,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val businessName: String,
    val currency: CurrencyCode,
    val branding: BrandingConfig,
    val terminology: TerminologyConfig,
    val tax: TaxConfig,
    val paymentMethods: List<PaymentMethodConfig>,
    val language: String,
    val themeMode: ThemeMode,
    val enabledModules: Set<String>,
) {
    fun isModuleEnabled(module: String): Boolean = module in enabledModules
}
