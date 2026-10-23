package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13.5: the dashboard-card registry contract (spec section 11,
 * "cards must be configurable"). The shipped set holds only the card
 * whose data exists today; the gate semantics are pinned here with
 * synthetic cards so the financial/indicator cards inherit proven
 * behavior when their data sources land in later phases.
 */
class DashboardCardsRegistryTest {

    @Test
    fun shippedCardSetIsExact() {
        assertEquals(
            listOf(DashboardCardsRegistry.CURRENT_BUSINESS_ID),
            DashboardCardsRegistry.cards.map { it.id },
        )
    }

    @Test
    fun currentBusinessCardIsUngatedAndAlwaysVisible() {
        // No config key, no capability: visible under every gate mix,
        // exactly the pre-13.5 dashboard behavior.
        assertEquals(
            listOf(DashboardCardsRegistry.CURRENT_BUSINESS_ID),
            DashboardCardsRegistry.visibleDashboardCards(null, null).map { it.id },
        )
        assertEquals(
            listOf(DashboardCardsRegistry.CURRENT_BUSINESS_ID),
            DashboardCardsRegistry.visibleDashboardCards(emptySet(), emptySet()).map { it.id },
        )
    }

    @Test
    fun configGateHidesACardWhoseKeyIsNotEnabled() {
        val card = DashboardCard(
            id = "synthetic",
            titleRes = 0,
            configKey = FeatureFlagKeys.MODULE_ORDERS,
            viewCapability = null,
        )
        assertTrue(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = setOf(FeatureFlagKeys.MODULE_ORDERS),
                grantedCapabilities = null,
            ),
        )
        assertFalse(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = setOf(FeatureFlagKeys.MODULE_INVENTORY),
                grantedCapabilities = null,
            ),
        )
        // A null configuration set disables the gate entirely.
        assertTrue(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = null,
                grantedCapabilities = null,
            ),
        )
    }

    @Test
    fun capabilityGateHidesACardWhoseCapabilityIsNotGranted() {
        val card = DashboardCard(
            id = "synthetic",
            titleRes = 0,
            configKey = null,
            viewCapability = Capabilities.REPORTS_VIEW,
        )
        assertTrue(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = null,
                grantedCapabilities = Capabilities.ALL,
            ),
        )
        assertFalse(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = null,
                grantedCapabilities = emptySet(),
            ),
        )
        // A null grant set disables the gate entirely.
        assertTrue(
            DashboardCardsRegistry.isCardVisible(
                card,
                enabledConfigKeys = null,
                grantedCapabilities = null,
            ),
        )
    }
}
