package com.lubrcapp.lubrcapp.config

import com.lubrcapp.lubrcapp.core.designsystem.AppButtonStyle
import com.lubrcapp.lubrcapp.core.designsystem.AppCardStyle
import com.lubrcapp.lubrcapp.core.designsystem.DesignThemeMode
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentEnvironment
import com.lubrcapp.lubrcapp.whitelist.config.PaymentMethodConfig
import com.lubrcapp.lubrcapp.whitelist.config.TaxConfig
import com.lubrcapp.lubrcapp.whitelist.config.TerminologyConfig
import com.lubrcapp.lubrcapp.whitelist.config.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigMapperTest {

    @Test
    fun deploymentBrandingMapsToNeutralDesignSystemConfig() {
        val config = deployment().toDesignSystemConfig()

        assertEquals(0xFF123456.toInt(), config.palette.primary)
        assertEquals(0xFF654321.toInt(), config.palette.secondary)
        assertEquals(0xFF336699.toInt(), config.palette.accent)
        assertEquals(AppCardStyle.OUTLINED, config.cardStyle)
        assertEquals(AppButtonStyle.FLAT, config.buttonStyle)
        assertEquals(24, config.borderRadiusDp)
        assertEquals(DesignThemeMode.DARK, config.themeMode)
        assertEquals(1.20f, config.defaultTextScale)
        assertTrue(config.defaultLargeTouchTargets)
    }

    private fun deployment() = DeploymentConfig(
        appName = "Config Test",
        applicationId = "com.example.configtest",
        versionName = "1.0.0",
        versionCode = 1,
        apiEndpoint = "https://example.invalid/",
        environment = DeploymentEnvironment.DEVELOPMENT,
        firebaseProjectId = null,
        licenseId = null,
        includedModules = setOf("inventory"),
        branding = BrandingConfig(
            primaryColor = 0xFF123456.toInt(),
            secondaryColor = 0xFF654321.toInt(),
            accentColor = 0xFF336699.toInt(),
            successColor = 0xFF008844.toInt(),
            warningColor = 0xFFFFAA00.toInt(),
            errorColor = 0xFFCC0022.toInt(),
            fontFamily = "serif",
            cardStyle = "outlined",
            buttonStyle = "flat",
            borderRadius = 24,
        ),
        terminology = TerminologyConfig(
            sale = "Sale",
            invoice = "Invoice",
            taxInvoice = "Tax Invoice",
            cashMemo = "Cash Memo",
            quotation = "Quotation",
            estimate = "Estimate",
            customer = "Customer",
            supplier = "Supplier",
            party = "Party",
            item = "Item",
            category = "Category",
            brand = "Brand",
            unit = "Unit",
            warehouse = "Warehouse",
            paymentMode = "Payment Method",
            expenseCategory = "Expense Category",
            otherIncome = "Other Income",
        ),
        defaultTax = TaxConfig(
            enabled = false,
            label = "Tax",
            numberLabel = "Tax Number",
            defaultRate = Percentage.ZERO,
        ),
        defaultPaymentMethods = listOf(PaymentMethodConfig("cash", "Cash")),
        defaultLanguage = "en",
        defaultThemeMode = ThemeMode.DARK,
        supportEmail = null,
        supportPhone = null,
        termsUrl = null,
        privacyUrl = null,
        website = null,
        defaultTextScale = 1.20f,
        defaultLargeTouchTargets = true,
    )
}
