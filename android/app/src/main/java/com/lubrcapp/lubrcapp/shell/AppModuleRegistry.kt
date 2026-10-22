package com.lubrcapp.lubrcapp.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.feature.accounts.AccountsScreen
import com.lubrcapp.lubrcapp.feature.inventory.InventoryScreen
import com.lubrcapp.lubrcapp.feature.items.ItemsScreen
import com.lubrcapp.lubrcapp.feature.parties.PartiesScreen
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesScreen
import com.lubrcapp.lubrcapp.feature.sales.SalesScreen
import com.lubrcapp.lubrcapp.feature.syncstatus.CloudSyncScreen
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys

/**
 * One module the app shell can route to.
 *
 * The [id] is the stable navigation key: in the state-based shell it is
 * both the registry key and the route. [titleRes] is the shell label.
 * [configKey] is the module's key in the white-label configuration key
 * space ([FeatureFlagKeys]) when the configuration engine can gate this
 * module, or null when the module lives outside that key space and config
 * therefore cannot disable it. [viewCapability] is the access-control
 * capability that views the module, or null when none is declared.
 * [selectable] marks modules the user picks from the shell's module list;
 * the dashboard is registered but is the home surface, not a selection.
 * [content] renders the module for a resolved operational scope; the home
 * surface has none because the shell renders it with dashboard state and
 * shell callbacks instead of a bare scope.
 */
class AppModule(
    val id: String,
    @StringRes val titleRes: Int,
    val configKey: String?,
    val viewCapability: CapabilityKey?,
    val selectable: Boolean,
    val content: (@Composable (OperationalScope, () -> Unit) -> Unit)?,
)

/**
 * Phase 13 module registry: the single source the app shell reads to know
 * which modules exist, in which order, with which labels, and how each one
 * is gated. It replaces the shell's previously hardcoded module list and
 * dispatch table (master spec section 2: the application should be
 * configuration-driven, built as Enabled Modules -> Feature Flags ->
 * Permissions, not fixed screens wired to fixed functions).
 *
 * Visibility is data, computed by [visibleModules]. The shell currently
 * passes no configuration, so every selectable module is visible in
 * registry order — identical to the pre-registry shell. A later Phase 13
 * package can pass the resolved white-label configuration's enabled-module
 * set: entries whose [AppModule.configKey] is absent from that set are
 * hidden, while entries with a null config key stay visible because the
 * configuration engine cannot address them (mirroring
 * WhiteLabelConfigResolver, where business overrides can only disable
 * keys the deployment included).
 */
object AppModuleRegistry {

    const val DASHBOARD_ID = "dashboard"
    const val ITEMS_ID = "items"
    const val INVENTORY_ID = "inventory"
    const val PARTIES_ID = "parties"
    const val SALES_ID = "sales"
    const val PURCHASES_ID = "purchases"
    const val ACCOUNTS_ID = "accounts"
    const val CLOUD_SYNC_ID = "cloudsync"

    val modules: List<AppModule> = listOf(
        AppModule(
            id = DASHBOARD_ID,
            titleRes = R.string.nav_home_title,
            configKey = null,
            viewCapability = null,
            selectable = false,
            content = null,
        ),
        AppModule(
            id = ITEMS_ID,
            titleRes = R.string.nav_items_title,
            // The configuration key space has no items key; items is core
            // master data and cannot be disabled by configuration today.
            configKey = null,
            viewCapability = Capabilities.ITEMS_VIEW,
            selectable = true,
            content = { scope, back -> ItemsScreen(scope, back) },
        ),
        AppModule(
            id = INVENTORY_ID,
            titleRes = R.string.feature_inventory,
            configKey = FeatureFlagKeys.MODULE_INVENTORY,
            viewCapability = Capabilities.INVENTORY_VIEW,
            selectable = true,
            content = { scope, back -> InventoryScreen(scope, back) },
        ),
        AppModule(
            id = PARTIES_ID,
            titleRes = R.string.feature_customers,
            // The configuration key space has no parties key either.
            configKey = null,
            viewCapability = Capabilities.PARTIES_VIEW,
            selectable = true,
            content = { scope, back -> PartiesScreen(scope, back) },
        ),
        AppModule(
            id = SALES_ID,
            titleRes = R.string.feature_sales,
            // In the configuration key space the sales pipeline is the
            // orders module (quotations, sales orders, invoices).
            configKey = FeatureFlagKeys.MODULE_ORDERS,
            viewCapability = Capabilities.SALES_VIEW,
            selectable = true,
            content = { scope, back -> SalesScreen(scope, back) },
        ),
        AppModule(
            id = PURCHASES_ID,
            titleRes = R.string.feature_purchases,
            configKey = FeatureFlagKeys.MODULE_PURCHASES,
            viewCapability = Capabilities.PURCHASES_VIEW,
            selectable = true,
            content = { scope, back -> PurchasesScreen(scope, back) },
        ),
        AppModule(
            id = ACCOUNTS_ID,
            titleRes = R.string.feature_accounts,
            configKey = FeatureFlagKeys.MODULE_ACCOUNTING,
            viewCapability = Capabilities.ACCOUNTING_VIEW,
            selectable = true,
            content = { scope, back -> AccountsScreen(scope, back) },
        ),
        AppModule(
            id = CLOUD_SYNC_ID,
            titleRes = R.string.feature_cloud_sync,
            configKey = FeatureFlagKeys.MODULE_SYNC,
            viewCapability = Capabilities.SYNC_MANAGE,
            selectable = true,
            content = { scope, back -> CloudSyncScreen(scope, back) },
        ),
    )

    private val byId: Map<String, AppModule> = modules.associateBy { it.id }

    /** The registered module for [id], or null for null/unknown ids. */
    fun module(id: String?): AppModule? = id?.let { byId[it] }

    /** Modules the user can open from the shell, in registry order. */
    fun navigationModules(): List<AppModule> = modules.filter { it.selectable }

    /**
     * Selectable modules visible under [enabledConfigKeys]. A null set
     * means no configuration was supplied and every selectable module is
     * visible. Otherwise a module is hidden only when it declares a
     * [AppModule.configKey] that the set does not contain.
     */
    fun visibleModules(enabledConfigKeys: Set<String>? = null): List<AppModule> =
        navigationModules().filter { module ->
            val key = module.configKey
            enabledConfigKeys == null || key == null || key in enabledConfigKeys
        }
}
