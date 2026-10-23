package com.lubrcapp.lubrcapp.shell

import androidx.annotation.StringRes
import com.lubrcapp.lubrcapp.R
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey

/**
 * One dashboard card (master spec §11): "Dashboard cards must be
 * configurable." A card carries the same two gate fields as a module —
 * [configKey] in the white-label configuration key space (null when
 * configuration cannot address it) and [viewCapability] (null when no
 * capability guards it) — and the shell computes card visibility with
 * the same semantics as [AppModuleRegistry.isVisible]: a card shows only
 * when both gates pass it.
 */
class DashboardCard(
    val id: String,
    @StringRes val titleRes: Int,
    val configKey: String?,
    val viewCapability: CapabilityKey?,
)

/**
 * Phase 13.5 dashboard-card registry.
 *
 * Only cards whose data exists in this tree are registered. Spec §11's
 * financial cards (Today's Sales/Purchases, Receivable, Payable, Cash,
 * Bank) read from the transaction and posting engines that land in
 * Phases 17/20/22, and its indicators (Low Stock, Out of Stock, Pending
 * Orders, Overdue Payments) from Phases 19/18/22 — registering them now
 * would mean rendering invented numbers, so they join this registry
 * with their data sources, and the gates doc carries the mapping. The
 * one card that exists today is the current-business card the dashboard
 * already renders; 13.5 routes its visibility through this registry so
 * configurability is real on screen, not framework-only.
 */
object DashboardCardsRegistry {

    const val CURRENT_BUSINESS_ID = "current_business"

    val cards: List<DashboardCard> = listOf(
        DashboardCard(
            id = CURRENT_BUSINESS_ID,
            titleRes = R.string.dashboard_current_business_card,
            configKey = null,
            viewCapability = null,
        ),
    )

    private val byId: Map<String, DashboardCard> = cards.associateBy { it.id }

    /** The registered card for [id], or null for null/unknown ids. */
    fun card(id: String?): DashboardCard? = id?.let { byId[it] }

    /**
     * Whether [card] passes both visibility gates. Mirrors
     * [AppModuleRegistry.isVisible]: a null gate disables that gate;
     * otherwise a card is hidden only when it declares a gate value the
     * supplied set does not contain.
     */
    fun isCardVisible(
        card: DashboardCard,
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): Boolean {
        val configKey = card.configKey
        val configVisible =
            enabledConfigKeys == null || configKey == null || configKey in enabledConfigKeys
        val viewCapability = card.viewCapability
        val capabilityVisible =
            grantedCapabilities == null ||
                viewCapability == null ||
                viewCapability in grantedCapabilities
        return configVisible && capabilityVisible
    }

    /**
     * Cards visible under [enabledConfigKeys] and [grantedCapabilities],
     * in registry order — see [isCardVisible] for the gate semantics.
     */
    fun visibleDashboardCards(
        enabledConfigKeys: Set<String>? = null,
        grantedCapabilities: Set<CapabilityKey>? = null,
    ): List<DashboardCard> =
        cards.filter { card -> isCardVisible(card, enabledConfigKeys, grantedCapabilities) }
}
