package com.lubrcapp.lubrcapp.whitelist.config

import com.lubrcapp.lubrcapp.core.repository.BusinessFeatureFlag
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.common.AuditMetadata
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.FeatureFlagId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.SyncMetadata
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteLabelConfigResolverTest {
    private val parser = BusinessConfigurationParser()
    private val resolver = WhiteLabelConfigResolver()
    private val tenantId = TenantId("tenant-1")
    private val businessId = BusinessId("business-1")

    @Test
    fun businessOverridesLayerOnDeploymentButCannotEnableExcludedModules() {
        val deployment = deployment(
            appName = "Distributor App",
            applicationId = "com.example.distributor",
            modules = setOf("inventory", "purchases", "accounting"),
            itemLabel = "Product",
        )
        val business = business("Zafar Lubricants", "PKR")
        val settings = listOf(
            setting(WhiteLabelConfigKeys.BRAND_COMPANY_NAME, "Zafar Lubricants Pvt Ltd"),
            setting(WhiteLabelConfigKeys.BRAND_PRIMARY_COLOR, "#FF112233"),
            setting(WhiteLabelConfigKeys.TERM_ITEM, "Lubricant"),
            setting(WhiteLabelConfigKeys.TERM_CUSTOMER, "Dealer"),
            setting(WhiteLabelConfigKeys.TAX_ENABLED, "true"),
            setting(WhiteLabelConfigKeys.TAX_DEFAULT_RATE, "18"),
            setting(WhiteLabelConfigKeys.PAYMENT_METHODS, "cash:Cash,credit:Credit"),
        )
        val flags = listOf(
            flag("accounting", false),
            flag("online_store", true),
        )

        val resolved = resolver.resolve(
            deployment,
            parser.parse(business, settings, flags),
        )

        assertEquals("Distributor App", resolved.deployment.appName)
        assertEquals("Zafar Lubricants Pvt Ltd", resolved.branding.companyName)
        assertEquals(0xFF112233.toInt(), resolved.branding.primaryColor)
        assertEquals("Lubricant", resolved.terminology.item)
        assertEquals("Dealer", resolved.terminology.customer)
        assertTrue(resolved.tax.enabled)
        assertEquals(Percentage.of("18"), resolved.tax.defaultRate)
        assertEquals(listOf("cash", "credit"), resolved.paymentMethods.map { it.id })
        assertEquals(setOf("inventory", "purchases"), resolved.enabledModules)
        assertFalse(resolved.isModuleEnabled("online_store"))
    }

    @Test
    fun sameResolverSupportsDifferentDeploymentProducts() {
        val business = business("City Pharmacy", "PKR")
        val businessConfig = parser.parse(business, emptyList(), emptyList())

        val lubricant = resolver.resolve(
            deployment(
                appName = "Distributor App",
                applicationId = "com.example.distributor",
                modules = setOf("inventory", "purchases"),
                itemLabel = "Product",
            ),
            businessConfig,
        )
        val pharmacy = resolver.resolve(
            deployment(
                appName = "ABC Pharmacy",
                applicationId = "com.example.pharmacy",
                modules = setOf("inventory", "barcodes", "batch_tracking"),
                itemLabel = "Medicine",
            ),
            businessConfig,
        )

        assertEquals("Distributor App", lubricant.deployment.appName)
        assertEquals("Product", lubricant.terminology.item)
        assertEquals(setOf("inventory", "purchases"), lubricant.enabledModules)

        assertEquals("ABC Pharmacy", pharmacy.deployment.appName)
        assertEquals("Medicine", pharmacy.terminology.item)
        assertEquals(setOf("inventory", "barcodes", "batch_tracking"), pharmacy.enabledModules)
    }

    @Test
    fun malformedOptionalBusinessValuesFallBackSafely() {
        val deployment = deployment(
            appName = "Retail App",
            applicationId = "com.example.retail",
            modules = setOf("inventory"),
            itemLabel = "Item",
        )
        val businessConfig = parser.parse(
            business("Retailer", "PKR"),
            listOf(
                setting(WhiteLabelConfigKeys.BRAND_PRIMARY_COLOR, "not-a-color"),
                setting(WhiteLabelConfigKeys.TAX_DEFAULT_RATE, "-wrong"),
                setting(WhiteLabelConfigKeys.THEME_MODE, "unknown"),
                setting(WhiteLabelConfigKeys.PAYMENT_METHODS, "broken"),
            ),
            emptyList(),
        )

        val resolved = resolver.resolve(deployment, businessConfig)

        assertEquals(deployment.branding.primaryColor, resolved.branding.primaryColor)
        assertEquals(deployment.defaultTax.defaultRate, resolved.tax.defaultRate)
        assertEquals(deployment.defaultThemeMode, resolved.themeMode)
        assertEquals(deployment.defaultPaymentMethods, resolved.paymentMethods)
    }

    private fun deployment(
        appName: String,
        applicationId: String,
        modules: Set<String>,
        itemLabel: String,
    ) = DeploymentConfig(
        appName = appName,
        applicationId = applicationId,
        versionName = "1.0.0",
        versionCode = 1,
        apiEndpoint = "https://example.invalid/",
        environment = DeploymentEnvironment.DEVELOPMENT,
        firebaseProjectId = null,
        licenseId = null,
        includedModules = modules,
        branding = BrandingConfig(
            primaryColor = 0xFF101010.toInt(),
            secondaryColor = 0xFF202020.toInt(),
            accentColor = 0xFF303030.toInt(),
            successColor = 0xFF404040.toInt(),
            warningColor = 0xFF505050.toInt(),
            errorColor = 0xFF606060.toInt(),
            cardStyle = "default",
            buttonStyle = "elevated",
            borderRadius = 16,
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
            item = itemLabel,
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
        defaultThemeMode = ThemeMode.SYSTEM,
        supportEmail = null,
        supportPhone = null,
        termsUrl = null,
        privacyUrl = null,
        website = null,
    )

    private fun business(name: String, currency: String) = Business(
        id = businessId,
        tenantId = tenantId,
        name = name,
        businessType = "OTHER",
        currency = CurrencyCode.of(currency),
        timezone = ZoneId.of("Asia/Karachi"),
        metadata = metadata(),
    )

    private fun setting(key: String, value: String) = BusinessSetting(
        id = SettingId("setting-$key"),
        tenantId = tenantId,
        businessId = businessId,
        key = key,
        value = value,
        metadata = metadata(),
    )

    private fun flag(key: String, enabled: Boolean) = BusinessFeatureFlag(
        id = FeatureFlagId("flag-$key"),
        tenantId = tenantId,
        businessId = businessId,
        key = key,
        enabled = enabled,
        metadata = metadata(),
    )

    private fun metadata() = EntityMetadata(
        audit = AuditMetadata(Instant.parse("2026-10-08T00:00:00Z")),
        sync = SyncMetadata(
            deviceId = DeviceId("device-1"),
            status = SyncStatus.LOCAL_ONLY,
        ),
    )
}
