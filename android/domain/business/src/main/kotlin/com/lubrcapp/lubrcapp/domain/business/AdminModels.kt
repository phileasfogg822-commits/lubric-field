package com.lubrcapp.lubrcapp.domain.business

/**
 * Phase 30.5 — web admin + SaaS models
 * (§185). Pure types: the admin surface
 * is a set of areas, a subscription plan
 * declares which areas it includes, and
 * gating is data + rules only — no billing,
 * no payment processing, no paywall
 * enforcement here. The React/Next.js admin
 * app itself is a separate codebase; this
 * is the model it will consume.
 */
enum class AdminArea {
    COMPANY,
    REPORTS,
    USERS,
    INVENTORY,
    WHITE_LABEL,
    SUBSCRIPTION,
    SUPPORT,
    SETTINGS,
}

data class SubscriptionPlan(
    val id: String,
    val name: String = "",
    val allowedAreas: Set<AdminArea> = emptySet(),
)

data class Subscription(
    val businessId: String,
    val planId: String,
    val status: String = "ACTIVE",
)

data class WhiteLabelConfig(
    val businessId: String,
    val brandName: String = "",
    val primaryColor: String = "",
)

object AdminAccessOps {
    fun isActive(subscription: Subscription): Boolean =
        subscription.status == "ACTIVE"

    fun canAccess(
        plan: SubscriptionPlan,
        subscription: Subscription,
        area: AdminArea,
    ): Boolean = isActive(subscription) && area in plan.allowedAreas

    fun isBranded(config: WhiteLabelConfig): Boolean =
        config.brandName.isNotBlank()
}
