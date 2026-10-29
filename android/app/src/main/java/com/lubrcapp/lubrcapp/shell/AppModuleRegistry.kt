package com.lubrcapp.lubrcapp.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.feature.accounts.AccountsScreen
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.inventory.InventoryScreen
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.items.ItemsScreen
import com.lubrcapp.lubrcapp.feature.parties.PartiesScreen
import com.lubrcapp.lubrcapp.feature.purchases.PurchaseAccountOption
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesScreen
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesViewModel
import com.lubrcapp.lubrcapp.feature.sales.PostingAccountOption
import com.lubrcapp.lubrcapp.feature.sales.SalesScreen
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
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
/**
 * Spec §13 bottom-navigation slots. The registry is the single source for
 * which modules hold a bottom-bar slot: [HOME] is the dashboard home,
 * [ITEMS] and [PARTIES] are master-data destinations. The spec's
 * Transactions slot is deliberately absent — there is no transactions
 * module or screen in the tree (it is spec §14, a later phase); when it
 * lands, its registry entry will declare a slot and the bar will pick it
 * up with no shell change.
 */
enum class BottomNavSlot {
    HOME,
    ITEMS,
    PARTIES,
}

class AppModule(
    val id: String,
    @StringRes val titleRes: Int,
    val configKey: String?,
    val viewCapability: CapabilityKey?,
    val selectable: Boolean,
    val content: (@Composable (OperationalScope, () -> Unit) -> Unit)?,
    val bottomNavSlot: BottomNavSlot? = null,
)

/**
 * Phase 13 module registry: the single source the app shell reads to know
 * which modules exist, in which order, with which labels, and how each one
 * is gated. It replaces the shell's previously hardcoded module list and
 * dispatch table (master spec section 2: the application should be
 * configuration-driven, built as Enabled Modules -> Feature Flags ->
 * Permissions, not fixed screens wired to fixed functions).
 *
 * Visibility is data, computed by [visibleModules] from two independent
 * gates. The configuration gate (Phase 13.2) consumes the resolved
 * white-label configuration's enabled-module set: entries whose
 * [AppModule.configKey] is absent from that set are hidden, while entries
 * with a null config key stay visible because the configuration engine
 * cannot address them (mirroring WhiteLabelConfigResolver, where business
 * overrides can only disable keys the deployment included). The
 * capability gate (Phase 13.3) consumes the current user's granted
 * capabilities: entries whose [AppModule.viewCapability] is not granted
 * are hidden, while entries with a null view capability stay visible
 * because no capability guards them. Either gate may hide a module; a
 * module is visible only when both pass it. Passing null for a gate
 * disables that gate — the registry itself is policy-free, and the shell
 * decides what to supply (for capabilities it always supplies a set,
 * empty until authorization resolves: deny-by-default).
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
    const val PRINTING_ID = "printing"
    const val POS_ID = "pos"
    const val FIELD_SALES_ID = "fieldsales"
    const val DATA_ID = "data"
    const val NOTIFICATIONS_ID = "notifications"

    val modules: List<AppModule> = listOf(
        AppModule(
            id = DASHBOARD_ID,
            titleRes = R.string.nav_home_title,
            configKey = null,
            viewCapability = null,
            selectable = false,
            content = null,
            bottomNavSlot = BottomNavSlot.HOME,
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
            bottomNavSlot = BottomNavSlot.ITEMS,
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
            content = { scope, back ->
                val accountsViewModel: AccountsViewModel = hiltViewModel()
                val salesViewModel: SalesViewModel = hiltViewModel()
                PartiesScreen(
                    scope = scope,
                    onBack = back,
                    ledgerTabsProvider = { partyId ->
                        PartyLedgerTabsMapping.tabsFor(scope, partyId, salesViewModel, accountsViewModel)
                    },
                    balanceProvider = { partyId ->
                        PartyLedgerTabsMapping.balanceFor(scope, partyId, accountsViewModel)
                    },
                )
            },
            bottomNavSlot = BottomNavSlot.PARTIES,
        ),
        AppModule(
            id = SALES_ID,
            titleRes = R.string.feature_sales,
            // In the configuration key space the sales pipeline is the
            // orders module (quotations, sales orders, invoices).
            configKey = FeatureFlagKeys.MODULE_ORDERS,
            viewCapability = Capabilities.SALES_VIEW,
            selectable = true,
            // Phase 21.5 — the posting join: the shell alone can
            // see all three ViewModels, so it wires the screen's
            // posting callback to SalesPostingHandler and projects
            // the active accounts into picker options. The screen
            // keeps its own SalesViewModel instance so the handler
            // loads the same repository the screen displays.
            content = { scope, back ->
                val salesViewModel: SalesViewModel = hiltViewModel()
                val accountsViewModel: AccountsViewModel = hiltViewModel()
                val inventoryViewModel: InventoryViewModel = hiltViewModel()
                val accounts by accountsViewModel.observeAccounts(scope)
                    .collectAsState(initial = emptyList())
                SalesScreen(
                    scope = scope,
                    onBack = back,
                    viewModel = salesViewModel,
                    accountOptions = accounts
                        .filter { it.isActive }
                        .map { PostingAccountOption(it.id, it.name) },
                    onPostDocument = { document, postingAccounts, currency ->
                        SalesPostingHandler.postDocument(
                            scope = scope,
                            document = document,
                            accounts = postingAccounts,
                            currency = currency,
                            salesViewModel = salesViewModel,
                            accountsViewModel = accountsViewModel,
                            inventoryViewModel = inventoryViewModel,
                        )
                    },
                    onPostPayment = { payment, documentNumber, paymentAccounts ->
                        SalesPaymentHandler.postPayment(
                            scope = scope,
                            payment = payment,
                            documentNumber = documentNumber,
                            accounts = paymentAccounts,
                            accountsViewModel = accountsViewModel,
                        )
                    },
                )
            },
        ),
        AppModule(
            id = PURCHASES_ID,
            titleRes = R.string.feature_purchases,
            configKey = FeatureFlagKeys.MODULE_PURCHASES,
            viewCapability = Capabilities.PURCHASES_VIEW,
            selectable = true,
            content = { scope, back ->
                val purchasesViewModel: PurchasesViewModel = hiltViewModel()
                val accountsViewModel: AccountsViewModel = hiltViewModel()
                val accounts by accountsViewModel.observeAccounts(scope)
                    .collectAsState(initial = emptyList())
                PurchasesScreen(
                    scope = scope,
                    onBack = back,
                    viewModel = purchasesViewModel,
                    accountOptions = accounts
                        .filter { it.isActive }
                        .map { PurchaseAccountOption(it.id, it.name) },
                    onPostPayment = { payment, documentNumber, paymentAccounts ->
                        PurchasePostingHandler.postPayment(
                            scope = scope,
                            payment = payment,
                            documentNumber = documentNumber,
                            accounts = paymentAccounts,
                            accountsViewModel = accountsViewModel,
                        )
                    },
                )
            },
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
        AppModule(
            id = PRINTING_ID,
            titleRes = R.string.feature_printing,
            configKey = FeatureFlagKeys.MODULE_PRINTING,
            viewCapability = Capabilities.PRINTING_VIEW,
            selectable = true,
            content = { scope, back -> PrintingPreviewContent(scope, back) },
        ),
        AppModule(
            id = POS_ID,
            titleRes = R.string.feature_pos,
            // The counter is the sales pipeline's POS mode: it
            // reuses the orders config key and the sales view
            // capability rather than minting a new flag, so no
            // deployment roster changes.
            configKey = FeatureFlagKeys.MODULE_ORDERS,
            viewCapability = Capabilities.SALES_VIEW,
            selectable = true,
            content = { scope, back -> PosCounterContent(scope, back) },
        ),
        AppModule(
            id = FIELD_SALES_ID,
            titleRes = R.string.feature_fieldsales,
            // The field sales dashboard is the sales pipeline's
            // field mode: it reuses the orders config key and
            // the sales view capability rather than minting a
            // new flag, so no deployment roster changes.
            configKey = FeatureFlagKeys.MODULE_ORDERS,
            viewCapability = Capabilities.SALES_VIEW,
            selectable = true,
            content = { scope, back -> FieldSalesContent(scope, back) },
        ),
        AppModule(
            id = DATA_ID,
            titleRes = R.string.feature_data,
            // Data tools ride the backup config key that
            // already exists in the flag set, so no flag
            // roster changes; the capability is new (see
            // DATA_VIEW) because no existing view
            // capability covers search, export and backup.
            configKey = FeatureFlagKeys.MODULE_BACKUP,
            viewCapability = Capabilities.DATA_VIEW,
            selectable = true,
            content = { scope, back -> DataToolsContent(scope, back) },
        ),
        AppModule(
            id = NOTIFICATIONS_ID,
            titleRes = R.string.feature_notifications,
            // Notifications ride the notifications config
            // key that already exists in the flag set, so
            // no flag roster changes; the capability is
            // new (see NOTIFICATIONS_VIEW) because no
            // existing view capability covers the center.
            configKey = FeatureFlagKeys.MODULE_NOTIFICATIONS,
            viewCapability = Capabilities.NOTIFICATIONS_VIEW,
            selectable = true,
            content = { scope, back -> NotificationsContent(scope, back) },
        ),
    )

    private val byId: Map<String, AppModule> = modules.associateBy { it.id }

    /** The registered module for [id], or null for null/unknown ids. */
    fun module(id: String?): AppModule? = id?.let { byId[it] }

    /** Modules the user can open from the shell, in registry order. */
    fun navigationModules(): List<AppModule> = modules.filter { it.selectable }

    /**
     * Whether [module] passes both visibility gates. [enabledConfigKeys]
     * is the resolved configuration's enabled-module set; a null set
     * disables the configuration gate, and otherwise a module is hidden
     * only when it declares a [AppModule.configKey] the set does not
     * contain. [grantedCapabilities] is the current user's granted
     * capability set; a null set disables the capability gate, and
     * otherwise a module is hidden only when it declares a
     * [AppModule.viewCapability] the set does not contain.
     */
    fun isVisible(
        module: AppModule,
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): Boolean {
        val configKey = module.configKey
        val configVisible =
            enabledConfigKeys == null || configKey == null || configKey in enabledConfigKeys
        val viewCapability = module.viewCapability
        val capabilityVisible =
            grantedCapabilities == null ||
                viewCapability == null ||
                viewCapability in grantedCapabilities
        return configVisible && capabilityVisible
    }

    /**
     * Selectable modules visible under [enabledConfigKeys] and
     * [grantedCapabilities] — see [isVisible] for the gate semantics.
     */
    fun visibleModules(
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): List<AppModule> =
        navigationModules().filter { module ->
            isVisible(module, enabledConfigKeys, grantedCapabilities)
        }

    /**
     * Bottom-navigation destinations for the shell bar (Phase 13.4): the
     * registered modules that declare a [BottomNavSlot], in slot order,
     * each still passing the full [isVisible] gates — a module hidden by
     * configuration or capability is never reachable from the bar. The
     * "More" destination is not a registry module; the shell renders it
     * as the overflow surface for the rest of [visibleModules].
     */
    fun bottomNavModules(
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): List<AppModule> =
        modules
            .filter { module ->
                module.bottomNavSlot != null &&
                    isVisible(module, enabledConfigKeys, grantedCapabilities)
            }
            .sortedBy { it.bottomNavSlot!!.ordinal }
}
