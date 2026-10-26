package com.lubrcapp.lubrcapp.domain.sales

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagerOverrideTest {

    @Test
    fun priceOverrideAllowedWithCapability() {
        assertEquals(
            OverrideResult.Allowed,
            ManagerOverride.evaluate(
                OverrideRequest(OverrideKind.PRICE, "rate 100 -> 90"),
                hasOverrideCapability = true,
            ),
        )
    }

    @Test
    fun priceOverrideRequiresApprovalWithoutCapability() {
        assertEquals(
            OverrideResult.RequiresManagerApproval,
            ManagerOverride.evaluate(
                OverrideRequest(OverrideKind.PRICE),
                hasOverrideCapability = false,
            ),
        )
    }

    @Test
    fun discountOverrideAllowedWithCapability() {
        assertEquals(
            OverrideResult.Allowed,
            ManagerOverride.evaluate(
                OverrideRequest(OverrideKind.DISCOUNT, "25%"),
                hasOverrideCapability = true,
            ),
        )
    }

    @Test
    fun discountOverrideRequiresApprovalWithoutCapability() {
        assertEquals(
            OverrideResult.RequiresManagerApproval,
            ManagerOverride.evaluate(
                OverrideRequest(OverrideKind.DISCOUNT),
                hasOverrideCapability = false,
            ),
        )
    }
}
