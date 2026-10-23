package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13.5: the quick-actions registry contract. Every shipped action
 * targets a real registry module; visibility is the composition of the
 * target module's gates (configuration AND capability, via
 * AppModuleRegistry) with the action's own create/manage capability —
 * spec section 12's "configurable per role and business".
 */
class QuickActionsRegistryTest {

    @Test
    fun shippedActionSetIsExact() {
        assertEquals(
            listOf(
                QuickActionsRegistry.SALE_ID,
                QuickActionsRegistry.PURCHASE_ID,
                QuickActionsRegistry.ORDER_ID,
                QuickActionsRegistry.PARTY_ID,
                QuickActionsRegistry.ITEM_ID,
                QuickActionsRegistry.STOCK_ADJUSTMENT_ID,
                QuickActionsRegistry.EXPENSE_ID,
                QuickActionsRegistry.INCOME_ID,
            ),
            QuickActionsRegistry.actions.map { it.id },
        )
    }

    @Test
    fun everyActionTargetsAnExistingSelectableModule() {
        QuickActionsRegistry.actions.forEach { action ->
            val target = AppModuleRegistry.module(action.targetModuleId)
            assertNotNull("action ${action.id} targets unknown module", target)
            assertTrue(
                "action ${action.id} target must be selectable",
                target!!.selectable,
            )
        }
    }

    @Test
    fun fullGrantsAndNoConfigShowEveryAction() {
        assertEquals(
            QuickActionsRegistry.actions.map { it.id },
            QuickActionsRegistry.visibleQuickActions(null, Capabilities.ALL).map { it.id },
        )
    }

    @Test
    fun emptyGrantsShowNoAction() {
        // Every shipped action declares a create/manage capability, and
        // the grant set doubles as the modules' capability gate, so an
        // empty set (authorization unresolved or denied) shows nothing —
        // deny-by-default, matching 13.3.
        assertTrue(
            QuickActionsRegistry.visibleQuickActions(null, emptySet()).isEmpty(),
        )
    }

    @Test
    fun missingActionCapabilityHidesOnlyThatAction() {
        // The purchases module stays viewable (purchases.view granted);
        // only the action's own manage capability is missing.
        val grants = Capabilities.ALL - Capabilities.PURCHASES_MANAGE
        val visible = QuickActionsRegistry.visibleQuickActions(null, grants).map { it.id }
        assertTrue(QuickActionsRegistry.PURCHASE_ID !in visible)
        assertEquals(QuickActionsRegistry.actions.size - 1, visible.size)
    }

    @Test
    fun hiddenTargetModuleHidesItsActions() {
        // Deny the purchases VIEW capability: the module leaves the shell
        // (13.3), so its quick action leaves too even though the manage
        // capability is still granted.
        val grants = Capabilities.ALL - Capabilities.PURCHASES_VIEW
        val visible = QuickActionsRegistry.visibleQuickActions(null, grants).map { it.id }
        assertTrue(QuickActionsRegistry.PURCHASE_ID !in visible)
    }

    @Test
    fun configurationGatingTheModuleHidesItsActions() {
        // Business configuration enables inventory, orders (sales),
        // accounting and sync only: purchases is configured out (13.2),
        // so + Purchase disappears while the rest stay.
        val enabled = setOf(
            FeatureFlagKeys.MODULE_INVENTORY,
            FeatureFlagKeys.MODULE_ORDERS,
            FeatureFlagKeys.MODULE_ACCOUNTING,
            FeatureFlagKeys.MODULE_SYNC,
        )
        val visible =
            QuickActionsRegistry.visibleQuickActions(enabled, Capabilities.ALL).map { it.id }
        assertTrue(QuickActionsRegistry.PURCHASE_ID !in visible)
        assertTrue(QuickActionsRegistry.SALE_ID in visible)
        assertTrue(QuickActionsRegistry.EXPENSE_ID in visible)
    }

    @Test
    fun saleAndOrderShareTheSalesModule() {
        // Documented registry decision: in this tree the sales pipeline
        // is the orders module, so + Sale and + Order open the same real
        // surface instead of one of them pointing at nothing.
        val byId = QuickActionsRegistry.actions.associateBy { it.id }
        assertEquals(
            byId.getValue(QuickActionsRegistry.SALE_ID).targetModuleId,
            byId.getValue(QuickActionsRegistry.ORDER_ID).targetModuleId,
        )
        assertEquals(
            AppModuleRegistry.SALES_ID,
            byId.getValue(QuickActionsRegistry.ORDER_ID).targetModuleId,
        )
    }
}
