package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingOverrides
import javax.inject.Inject

class BusinessConfigurationParser @Inject constructor() {
    fun parse(
        business: Business,
        settings: List<BusinessSetting>,
        featureFlags: List<BusinessFeatureFlag>,
    ): BusinessConfig {
        val values = settings.associate { it.key to it.value }

        fun text(key: String): String? = ConfigurationCodecs.optional(values[key])
        fun color(key: String): Int? = ConfigurationCodecs.argb(values[key])

        val borderRadius = values[WhiteLabelConfigKeys.BRAND_BORDER_RADIUS]
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }

        return BusinessConfig(
            tenantId = business.tenantId,
            businessId = business.id,
            businessName = business.name,
            currency = business.currency,
            branding = BrandingOverrides(
                companyName = text(WhiteLabelConfigKeys.BRAND_COMPANY_NAME),
                logoUri = text(WhiteLabelConfigKeys.BRAND_LOGO_URI),
                primaryColor = color(WhiteLabelConfigKeys.BRAND_PRIMARY_COLOR),
                secondaryColor = color(WhiteLabelConfigKeys.BRAND_SECONDARY_COLOR),
                accentColor = color(WhiteLabelConfigKeys.BRAND_ACCENT_COLOR),
                successColor = color(WhiteLabelConfigKeys.BRAND_SUCCESS_COLOR),
                warningColor = color(WhiteLabelConfigKeys.BRAND_WARNING_COLOR),
                errorColor = color(WhiteLabelConfigKeys.BRAND_ERROR_COLOR),
                fontFamily = text(WhiteLabelConfigKeys.BRAND_FONT_FAMILY),
                cardStyle = text(WhiteLabelConfigKeys.BRAND_CARD_STYLE),
                buttonStyle = text(WhiteLabelConfigKeys.BRAND_BUTTON_STYLE),
                borderRadius = borderRadius,
            ),
            terminology = TerminologyOverrides(
                sale = text(WhiteLabelConfigKeys.TERM_SALE),
                invoice = text(WhiteLabelConfigKeys.TERM_INVOICE),
                taxInvoice = text(WhiteLabelConfigKeys.TERM_TAX_INVOICE),
                cashMemo = text(WhiteLabelConfigKeys.TERM_CASH_MEMO),
                quotation = text(WhiteLabelConfigKeys.TERM_QUOTATION),
                estimate = text(WhiteLabelConfigKeys.TERM_ESTIMATE),
                customer = text(WhiteLabelConfigKeys.TERM_CUSTOMER),
                supplier = text(WhiteLabelConfigKeys.TERM_SUPPLIER),
                party = text(WhiteLabelConfigKeys.TERM_PARTY),
                item = text(WhiteLabelConfigKeys.TERM_ITEM),
                category = text(WhiteLabelConfigKeys.TERM_CATEGORY),
                brand = text(WhiteLabelConfigKeys.TERM_BRAND),
                unit = text(WhiteLabelConfigKeys.TERM_UNIT),
                warehouse = text(WhiteLabelConfigKeys.TERM_WAREHOUSE),
                paymentMode = text(WhiteLabelConfigKeys.TERM_PAYMENT_MODE),
                expenseCategory = text(WhiteLabelConfigKeys.TERM_EXPENSE_CATEGORY),
                otherIncome = text(WhiteLabelConfigKeys.TERM_OTHER_INCOME),
            ),
            tax = TaxOverrides(
                enabled = ConfigurationCodecs.boolean(values[WhiteLabelConfigKeys.TAX_ENABLED]),
                label = text(WhiteLabelConfigKeys.TAX_LABEL),
                numberLabel = text(WhiteLabelConfigKeys.TAX_NUMBER_LABEL),
                defaultRate = ConfigurationCodecs.percentage(values[WhiteLabelConfigKeys.TAX_DEFAULT_RATE]),
            ),
            paymentMethods = ConfigurationCodecs.paymentMethods(values[WhiteLabelConfigKeys.PAYMENT_METHODS]),
            language = text(WhiteLabelConfigKeys.LANGUAGE),
            themeMode = ThemeMode.parseOrNull(values[WhiteLabelConfigKeys.THEME_MODE]),
            moduleOverrides = featureFlags.associate { it.key to it.enabled },
        )
    }
}
