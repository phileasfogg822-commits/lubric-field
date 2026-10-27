package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingConfig
import com.lubrcapp.lubrcapp.whitelist.branding.BrandingOverrides
import com.lubrcapp.lubrcapp.whitelist.config.BusinessConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentConfig
import com.lubrcapp.lubrcapp.whitelist.config.DeploymentEnvironment
import com.lubrcapp.lubrcapp.whitelist.config.PaymentMethodConfig
import com.lubrcapp.lubrcapp.whitelist.config.TaxConfig
import com.lubrcapp.lubrcapp.whitelist.config.TaxOverrides
import com.lubrcapp.lubrcapp.whitelist.config.TerminologyConfig
import com.lubrcapp.lubrcapp.whitelist.config.TerminologyOverrides
import com.lubrcapp.lubrcapp.whitelist.config.ThemeMode
import com.lubrcapp.lubrcapp.whitelist.config.WhiteLabelConfigResolver
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resolution-to-visibility path (Phase 13.2): what the production
 * [WhiteLabelConfigResolver] computes from a deployment (Level A) and a
 * business's module overrides (Level B) is exactly what
 * [AppModuleRegistry.visibleModules] consumes in the shell. These tests
 * pin that composition end to end with the real resolver and the real
 * registry — no test double sits between configuration and visibility.
 */
class ResolvedShellVisibilityTest {

    private val resolver = WhiteLabelConfigResolver()

    private val gatedKeys = setOf(
        FeatureFlagKeys.MODULE_INVENTORY,
        FeatureFlagKeys.MODULE_ORDERS,
        FeatureFlagKeys.MODULE_PURCHASES,
        FeatureFlagKeys.MODULE_ACCOUNTING,
        FeatureFlagKeys.MODULE_SYNC,
        FeatureFlagKeys.MODULE_PRINTING,
    )

    private fun visibleIds(
        deploymentModules: Set<String>,
        overrides: Map<String, Boolean> = emptyMap(),
    ): List<String> {
        val resolved = resolver.resolve(
            deployment = deployment(deploymentModules),
            business = business(overrides),
        )
        return AppModuleRegistry.visibleModules(resolved.enabledModules).map { it.id }
    }

    @Test
    fun deploymentExcludingGatedModulesHidesThemInTheShell() {
        // Level A ships only inventory and accounting: sales (orders),
        // purchases and cloud sync never reach the shell.
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
            ),
            visibleIds(setOf(FeatureFlagKeys.MODULE_INVENTORY, FeatureFlagKeys.MODULE_ACCOUNTING)),
        )
    }

    @Test
    fun businessOverrideDisablesAnIncludedModule() {
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.SALES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
                AppModuleRegistry.CLOUD_SYNC_ID,
                AppModuleRegistry.PRINTING_ID,
                AppModuleRegistry.POS_ID,
                AppModuleRegistry.FIELD_SALES_ID,
            ),
            visibleIds(gatedKeys, mapOf(FeatureFlagKeys.MODULE_PURCHASES to false)),
        )
    }

    @Test
    fun businessOverrideTrueKeepsIncludedModuleButCannotEnableExcludedOne() {
        // Resolver contract: overrides only ever disable. An explicit
        // true keeps an included module, and a true for a module the
        // deployment never included does not add it.
        val resolved = resolver.resolve(
            deployment = deployment(setOf(FeatureFlagKeys.MODULE_INVENTORY)),
            business = business(
                mapOf(
                    FeatureFlagKeys.MODULE_INVENTORY to true,
                    FeatureFlagKeys.MODULE_ORDERS to true,
                ),
            ),
        )

        assertEquals(setOf(FeatureFlagKeys.MODULE_INVENTORY), resolved.enabledModules)
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            AppModuleRegistry.visibleModules(resolved.enabledModules).map { it.id },
        )
    }

    @Test
    fun unknownConfigurationKeysAreIgnoredByTheShell() {
        // Deployments include keys with no shell module (reports, users,
        // future keys); the registry simply has nothing to gate with
        // them, and overrides for unknown keys change nothing. The
        // deployment covers every real module key — backup joined
        // with the data module — so the unknown keys are the only
        // thing that could change the list, and they do not.
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            visibleIds(
                gatedKeys + setOf(
                    FeatureFlagKeys.MODULE_REPORTS,
                    FeatureFlagKeys.MODULE_BACKUP,
                    FeatureFlagKeys.MODULE_NOTIFICATIONS,
                    "future_module",
                ),
                mapOf("another_unknown" to false),
            ),
        )
    }

    @Test
    fun itemsAndPartiesSurviveAnEmptyResolvedSet() {
        // The configuration key space has no items or parties key, so no
        // deployment or business configuration can hide them today.
        assertEquals(
            listOf(AppModuleRegistry.ITEMS_ID, AppModuleRegistry.PARTIES_ID),
            visibleIds(emptySet()),
        )
    }

    @Test
    fun shippedDeploymentConfigurationKeepsEveryModuleVisible() {
        // Mirrors white-label/deployment.properties includedModules at
        // the time of writing: with the shipped deployment and no
        // business overrides, Phase 13.2 gating changes nothing.
        val shipped = setOf(
            "inventory", "orders", "purchases", "pays", "accounting",
            "reports", "users", "sync", "backup", "notifications",
            "printing", "barcodes", "returns", "expenses", "other_income",
            "cash_bank", "tax",
        )
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            visibleIds(shipped),
        )
    }

    private fun deployment(modules: Set<String>) = DeploymentConfig(
        appName = "Shell Test App",
        applicationId = "com.example.shelltest",
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
        defaultThemeMode = ThemeMode.SYSTEM,
        supportEmail = null,
        supportPhone = null,
        termsUrl = null,
        privacyUrl = null,
        website = null,
    )

    private fun business(overrides: Map<String, Boolean>) = BusinessConfig(
        tenantId = TenantId("tenant-1"),
        businessId = BusinessId("business-1"),
        businessName = "Shell Test Business",
        currency = CurrencyCode("USD"),
        branding = BrandingOverrides(),
        terminology = TerminologyOverrides(),
        tax = TaxOverrides(),
        paymentMethods = null,
        language = null,
        themeMode = null,
        moduleOverrides = overrides,
    )
}
