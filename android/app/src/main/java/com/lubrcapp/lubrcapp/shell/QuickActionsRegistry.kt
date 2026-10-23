package com.lubrcapp.lubrcapp.shell

import androidx.annotation.StringRes
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey

/**
 * One home quick action (master spec §12): a labelled shortcut that opens
 * the module where the record is created. [targetModuleId] is a registry
 * module id in [AppModuleRegistry]; the shell dispatches the tap through
 * the same `selectedFeature` state and the same visibility guards as the
 * module list and the bottom navigation, so an action can never open a
 * module the gates hide. [requiredCapability] is the create/manage
 * capability the action needs on top of the target module's own gates —
 * this is spec §12's "configurable per role"; per-business
 * configurability comes from the target module's configuration gate
 * (Phase 13.2), so hiding a module hides its actions too.
 */
class QuickAction(
    val id: String,
    @StringRes val titleRes: Int,
    val targetModuleId: String,
    val requiredCapability: CapabilityKey?,
)

/**
 * Phase 13.5 quick-actions registry: the single source the shell reads
 * for spec §12's home quick actions, mirroring [AppModuleRegistry]'s
 * role for modules.
 *
 * Only actions whose target surface exists in this tree are registered.
 * Spec §12 also names Payment In / Payment Out and the distinct document
 * flows (Quotation, Sales Order, Purchase Order, Delivery Challan):
 * there is no payments surface yet (receivables/payables are Phase 22)
 * and no distinct document engines (Phases 18/21/27), so those actions
 * are deliberately absent rather than wired to dead ends — they join
 * this registry when their surfaces land, the same rule the bottom
 * navigation applied to the Transactions slot in 13.4. "+ Order" targets
 * the sales module because in this tree the sales pipeline IS the orders
 * module (see the registry's note on the sales entry).
 */
object QuickActionsRegistry {

    const val SALE_ID = "sale"
    const val PURCHASE_ID = "purchase"
    const val ORDER_ID = "order"
    const val PARTY_ID = "party"
    const val ITEM_ID = "item"
    const val STOCK_ADJUSTMENT_ID = "stock_adjustment"
    const val EXPENSE_ID = "expense"
    const val INCOME_ID = "income"

    val actions: List<QuickAction> = listOf(
        QuickAction(
            id = SALE_ID,
            titleRes = R.string.quick_add_sale,
            targetModuleId = AppModuleRegistry.SALES_ID,
            requiredCapability = Capabilities.SALES_CREATE,
        ),
        QuickAction(
            id = PURCHASE_ID,
            titleRes = R.string.quick_add_purchase,
            targetModuleId = AppModuleRegistry.PURCHASES_ID,
            requiredCapability = Capabilities.PURCHASES_MANAGE,
        ),
        QuickAction(
            id = ORDER_ID,
            titleRes = R.string.quick_add_order,
            targetModuleId = AppModuleRegistry.SALES_ID,
            requiredCapability = Capabilities.SALES_CREATE,
        ),
        QuickAction(
            id = PARTY_ID,
            titleRes = R.string.quick_party,
            targetModuleId = AppModuleRegistry.PARTIES_ID,
            requiredCapability = Capabilities.PARTIES_MANAGE,
        ),
        QuickAction(
            id = ITEM_ID,
            titleRes = R.string.quick_item,
            targetModuleId = AppModuleRegistry.ITEMS_ID,
            requiredCapability = Capabilities.ITEMS_MANAGE,
        ),
        QuickAction(
            id = STOCK_ADJUSTMENT_ID,
            titleRes = R.string.quick_stock_adjustment,
            targetModuleId = AppModuleRegistry.INVENTORY_ID,
            requiredCapability = Capabilities.INVENTORY_ADJUST,
        ),
        QuickAction(
            id = EXPENSE_ID,
            titleRes = R.string.quick_expense,
            targetModuleId = AppModuleRegistry.ACCOUNTS_ID,
            requiredCapability = Capabilities.ACCOUNTING_MANAGE,
        ),
        QuickAction(
            id = INCOME_ID,
            titleRes = R.string.quick_income,
            targetModuleId = AppModuleRegistry.ACCOUNTS_ID,
            requiredCapability = Capabilities.ACCOUNTING_MANAGE,
        ),
    )

    /**
     * Actions visible under the shell's composed gates: the target module
     * must pass [AppModuleRegistry.isVisible] for [enabledConfigKeys] and
     * [grantedCapabilities] (a null gate disables that gate, exactly as
     * in the registry), and the action's [QuickAction.requiredCapability]
     * must be granted. The shell always passes its real grant set (empty
     * until authorization resolves), so before resolution no action
     * shows — deny-by-default, matching 13.3.
     */
    fun visibleQuickActions(
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): List<QuickAction> =
        actions.filter { action ->
            val target = AppModuleRegistry.module(action.targetModuleId)
            val moduleVisible =
                target != null &&
                    AppModuleRegistry.isVisible(target, enabledConfigKeys, grantedCapabilities)
            val capability = action.requiredCapability
            val capabilityGranted =
                grantedCapabilities == null || capability == null || capability in grantedCapabilities
            moduleVisible && capabilityGranted
        }
}
