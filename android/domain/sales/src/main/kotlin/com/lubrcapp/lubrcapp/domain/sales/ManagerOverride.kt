package com.lubrcapp.lubrcapp.domain.sales

/**
 * Phase 26.6 — manager overrides (§153 price override,
 * §154 discount approval): a missing permission means a
 * manager approval is required. Pure decision only: no
 * PIN store, no credential check is invented here.
 *
 * The existing mechanism is capabilities, not PINs: the
 * discount side already has `invoice.discount.override`
 * in domain/user, and 26.6 adds the missing price side
 * (`invoice.price.override`) beside it. The caller maps
 * the acting user's grants to [hasOverrideCapability] —
 * true when the user holds the override capability for
 * the request's kind, or when a manager has approved
 * this request out of band (the screen collects that
 * approval at 26.7). Without it, the result is
 * [OverrideResult.RequiresManagerApproval] and the cart
 * edit must not apply.
 */
enum class OverrideKind {
    PRICE,
    DISCOUNT,
}

data class OverrideRequest(
    val kind: OverrideKind,
    val detail: String = "",
)

sealed interface OverrideResult {
    data object Allowed : OverrideResult
    data object RequiresManagerApproval : OverrideResult
}

object ManagerOverride {

    fun evaluate(request: OverrideRequest, hasOverrideCapability: Boolean): OverrideResult =
        if (hasOverrideCapability) {
            OverrideResult.Allowed
        } else {
            OverrideResult.RequiresManagerApproval
        }
}
