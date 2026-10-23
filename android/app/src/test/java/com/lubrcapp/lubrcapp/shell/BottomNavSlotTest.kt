package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13.4: the bottom-navigation slot metadata. The bar is
 * registry-driven, so the slot assignments, the slot order, and the
 * visibility filtering are pinned here — including the deliberate
 * absence of the spec's Transactions slot (no transactions module exists;
 * it is spec §14) and the guarantee that a module hidden by either gate
 * never appears in the bar.
 */
class BottomNavSlotTest {

    @Test
    fun slotAssignmentsAreExact() {
        val byId = AppModuleRegistry.modules.associateBy { it.id }
        assertEquals(
            BottomNavSlot.HOME,
            byId.getValue(AppModuleRegistry.DASHBOARD_ID).bottomNavSlot,
        )
        assertEquals(
            BottomNavSlot.ITEMS,
            byId.getValue(AppModuleRegistry.ITEMS_ID).bottomNavSlot,
        )
        assertEquals(
            BottomNavSlot.PARTIES,
            byId.getValue(AppModuleRegistry.PARTIES_ID).bottomNavSlot,
        )
    }

    @Test
    fun onlyHomeItemsPartiesHoldSlots() {
        val slotted = AppModuleRegistry.modules.filter { it.bottomNavSlot != null }
        assertEquals(
            setOf(
                AppModuleRegistry.DASHBOARD_ID,
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            slotted.map { it.id }.toSet(),
        )
    }

    @Test
    fun slottedModulesAreReachableDestinations() {
        // Dashboard is the home surface (not a selection); items and
        // parties are selectable destinations — a bar tap hands the id to
        // the same selectedFeature state the module list uses.
        val byId = AppModuleRegistry.modules.associateBy { it.id }
        assertTrue(byId.getValue(AppModuleRegistry.ITEMS_ID).selectable)
        assertTrue(byId.getValue(AppModuleRegistry.PARTIES_ID).selectable)
    }

    @Test
    fun bottomNavModulesComeBackInSlotOrder() {
        assertEquals(
            listOf(
                AppModuleRegistry.DASHBOARD_ID,
                AppModuleRegistry.ITEMS_ID,
                AppModuleRegistry.PARTIES_ID,
            ),
            AppModuleRegistry.bottomNavModules(null, null).map { it.id },
        )
    }

    @Test
    fun capabilityGateRemovesSlottedModulesFromTheBar() {
        // Items and parties declare view capabilities; the dashboard
        // declares none, so it survives even an empty grant set.
        assertEquals(
            listOf(AppModuleRegistry.DASHBOARD_ID),
            AppModuleRegistry.bottomNavModules(
                enabledConfigKeys = null,
                grantedCapabilities = emptySet(),
            ).map { it.id },
        )
        assertEquals(
            listOf(AppModuleRegistry.DASHBOARD_ID, AppModuleRegistry.ITEMS_ID),
            AppModuleRegistry.bottomNavModules(
                enabledConfigKeys = null,
                grantedCapabilities = setOf(Capabilities.ITEMS_VIEW),
            ).map { it.id },
        )
    }

    @Test
    fun bottomNavModulesNeverExceedVisibleModules() {
        // The parity pin: a bar destination must always be a destination
        // the composed visibility also shows, under every gate mix, so
        // the bar cannot open a module the gates hide.
        val gateMixes =
            listOf<Pair<Set<String>?, Set<CapabilityKey>?>>(
                null to null,
                null to emptySet<CapabilityKey>(),
                null to Capabilities.ALL,
                setOf(FeatureFlagKeys.MODULE_INVENTORY) to Capabilities.ALL,
                emptySet<String>() to emptySet(),
            )
        gateMixes.forEach { (configKeys, grants) ->
            val bar =
                AppModuleRegistry.bottomNavModules(configKeys, grants).map { it.id }.toSet()
            val visible =
                AppModuleRegistry.visibleModules(configKeys, grants).map { it.id }.toSet()
            // The dashboard is the home surface, not a "visible module"
            // selection, so the bar equals the visible selections plus
            // the dashboard home.
            val expected = visible + AppModuleRegistry.DASHBOARD_ID
            assertTrue(
                "bar $bar must stay within visible $expected for gates $configKeys/$grants",
                bar.all { it in expected },
            )
        }
    }
}
