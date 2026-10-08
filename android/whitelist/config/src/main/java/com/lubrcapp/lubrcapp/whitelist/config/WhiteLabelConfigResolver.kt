package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.whitelist.branding.applyOverrides
import javax.inject.Inject

class WhiteLabelConfigResolver @Inject constructor() {
    fun resolve(
        deployment: DeploymentConfig,
        business: BusinessConfig,
    ): ResolvedWhiteLabelConfig {
        val terminology = deployment.terminology.copy(
            sale = business.terminology.sale ?: deployment.terminology.sale,
            invoice = business.terminology.invoice ?: deployment.terminology.invoice,
            taxInvoice = business.terminology.taxInvoice ?: deployment.terminology.taxInvoice,
            cashMemo = business.terminology.cashMemo ?: deployment.terminology.cashMemo,
            quotation = business.terminology.quotation ?: deployment.terminology.quotation,
            estimate = business.terminology.estimate ?: deployment.terminology.estimate,
            customer = business.terminology.customer ?: deployment.terminology.customer,
            supplier = business.terminology.supplier ?: deployment.terminology.supplier,
            party = business.terminology.party ?: deployment.terminology.party,
            item = business.terminology.item ?: deployment.terminology.item,
            category = business.terminology.category ?: deployment.terminology.category,
            brand = business.terminology.brand ?: deployment.terminology.brand,
            unit = business.terminology.unit ?: deployment.terminology.unit,
            warehouse = business.terminology.warehouse ?: deployment.terminology.warehouse,
            paymentMode = business.terminology.paymentMode ?: deployment.terminology.paymentMode,
            expenseCategory = business.terminology.expenseCategory ?: deployment.terminology.expenseCategory,
            otherIncome = business.terminology.otherIncome ?: deployment.terminology.otherIncome,
        )

        val tax = deployment.defaultTax.copy(
            enabled = business.tax.enabled ?: deployment.defaultTax.enabled,
            label = business.tax.label ?: deployment.defaultTax.label,
            numberLabel = business.tax.numberLabel ?: deployment.defaultTax.numberLabel,
            defaultRate = business.tax.defaultRate ?: deployment.defaultTax.defaultRate,
        )

        val enabledModules = deployment.includedModules
            .filterTo(linkedSetOf()) { module -> business.moduleOverrides[module] != false }

        return ResolvedWhiteLabelConfig(
            deployment = deployment,
            tenantId = business.tenantId,
            businessId = business.businessId,
            businessName = business.businessName,
            currency = business.currency,
            branding = deployment.branding.applyOverrides(
                overrides = business.branding,
                businessNameFallback = business.businessName,
            ),
            terminology = terminology,
            tax = tax,
            paymentMethods = business.paymentMethods ?: deployment.defaultPaymentMethods,
            language = business.language ?: deployment.defaultLanguage,
            themeMode = business.themeMode ?: deployment.defaultThemeMode,
            enabledModules = enabledModules,
        )
    }
}
