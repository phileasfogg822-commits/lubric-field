package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composed shell visibility (Phase 13.3): the registry applies the
 * configuration gate (Phase 13.2) AND the capability gate together, so a
 * module reaches the shell only when the resolved configuration enables
 * it and the current user's grants include the capability that views it.
 * These tests pin the composition with the real registry data.
 */
class CapabilityGatedVisibilityTest {

    private val allConfig = FeatureFlagKeys.ALL
    private val allGrants = Capabilities.ALL

    private fun visibleIds(
        config: Set<String>?,
        grants: Set<CapabilityKey>?,
    ): List<String> = AppModuleRegistry.visibleModules(config, grants).map { it.id }

    @Test
    fun fullConfigurationAndFullGrantsShowEveryModule() {
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            visibleIds(allConfig, allGrants),
        )
    }

    @Test
    fun configVisibleButCapabilityDeniedModuleIsHidden() {
        // Configuration enables everything; the user's roles grant
        // everything except sales.view, so only Sales leaves the list.
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.PURCHASES_ID,
                AppModuleRegistry.ACCOUNTS_ID,
                AppModuleRegistry.CLOUD_SYNC_ID,
            ),
            visibleIds(allConfig, allGrants - Capabilities.SALES_VIEW),
        )
    }

    @Test
    fun capabilityGrantedButConfigHiddenModuleStaysHidden() {
        // Full grants cannot resurrect a module the configuration
        // disabled: with an empty resolved configuration only the two
        // config-ungated modules remain.
        assertEquals(
            listOf(AppModuleRegistry.ITEMS_ID, AppModuleRegistry.PARTIES_ID),
            visibleIds(emptySet(), allGrants),
        )
    }

    @Test
    fun theTwoGatesComposePerModule() {
        // Configuration enables inventory, orders (sales) and accounting;
        // grants exclude accounting.view. Accounts is config-visible but
        // capability-denied, so it drops out while sales stays.
        val config = setOf(
            FeatureFlagKeys.MODULE_INVENTORY,
            FeatureFlagKeys.MODULE_ORDERS,
            FeatureFlagKeys.MODULE_ACCOUNTING,
        )
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
                AppModuleRegistry.SALES_ID,
            ),
            visibleIds(config, allGrants - Capabilities.ACCOUNTING_VIEW),
        )
    }

    @Test
    fun emptyGrantsHideEverySelectableModule() {
        // The denied snapshot's grant set is empty, and every selectable
        // module declares a view capability — including items and
        // parties, which configuration cannot gate but capabilities can.
        assertEquals(
            emptyList<String>(),
            visibleIds(allConfig, emptySet()),
        )
    }

    @Test
    fun nullGrantsLeaveTheCapabilityGateInert() {
        // A caller that supplies no authorization result gets exactly the
        // Phase 13.2 configuration-only behavior; the shell itself never
        // passes null (it passes the empty set until authorization
        // resolves — deny-by-default lives in the shell, not here).
        assertEquals(
            listOf(
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.INVENTORY_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            visibleIds(setOf(FeatureFlagKeys.MODULE_INVENTORY), null),
        )
        assertEquals(
            AppModuleRegistry.navigationModules().map { it.id },
            visibleIds(null, null),
        )
    }

    @Test
    fun moduleWithoutViewCapabilityIsNeverCapabilityHidden() {
        // The dashboard declares no view capability (and no config key):
        // neither gate can hide it, whatever the sets contain.
        val dashboard = AppModuleRegistry.module(AppModuleRegistry.DASHBOARD_ID)!!
        assertTrue(AppModuleRegistry.isVisible(dashboard, null, emptySet()))
        assertTrue(AppModuleRegistry.isVisible(dashboard, emptySet(), emptySet()))
    }

    @Test
    fun dispatchVisibilityCheckRejectsHiddenModules() {
        // MainNavigation renders a selected module's surface only when
        // this same predicate passes, so a capability-hidden (or
        // config-hidden) module cannot be dispatched to directly.
        val sales = AppModuleRegistry.module(AppModuleRegistry.SALES_ID)!!
        assertTrue(
            AppModuleRegistry.isVisible(sales, allConfig, setOf(Capabilities.SALES_VIEW)),
        )
        assertFalse(
            AppModuleRegistry.isVisible(sales, allConfig, allGrants - Capabilities.SALES_VIEW),
        )
        assertFalse(
            AppModuleRegistry.isVisible(sales, emptySet(), setOf(Capabilities.SALES_VIEW)),
        )
    }
}
