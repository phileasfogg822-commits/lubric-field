package com.lubrcapp.lubrcapp.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminModelsTest {

    private val fullPlan = SubscriptionPlan(
        "pro",
        "Pro",
        allowedAreas = AdminArea.entries.toSet(),
    )
    private val active = Subscription("b1", "pro")
    private val expired = Subscription("b1", "pro", status = "EXPIRED")

    @Test
    fun areasMatchSpecOrder() {
        assertEquals(
            listOf(
                AdminArea.COMPANY,
                AdminArea.REPORTS,
                AdminArea.USERS,
                AdminArea.INVENTORY,
                AdminArea.WHITE_LABEL,
                AdminArea.SUBSCRIPTION,
                AdminArea.SUPPORT,
                AdminArea.SETTINGS,
            ),
            AdminArea.entries.toList(),
        )
    }

    @Test
    fun activeSubscriptionWithAllowedAreaCanAccess() {
        assertTrue(AdminAccessOps.canAccess(fullPlan, active, AdminArea.REPORTS))
    }

    @Test
    fun inactiveSubscriptionCannotAccess() {
        assertFalse(AdminAccessOps.canAccess(fullPlan, expired, AdminArea.REPORTS))
    }

    @Test
    fun disallowedAreaCannotAccess() {
        val limited = SubscriptionPlan("basic", "Basic", allowedAreas = setOf(AdminArea.COMPANY))
        assertFalse(AdminAccessOps.canAccess(limited, active, AdminArea.INVENTORY))
    }

    @Test
    fun emptyPlanAllowsNothing() {
        val empty = SubscriptionPlan("free", "Free")
        assertFalse(AdminAccessOps.canAccess(empty, active, AdminArea.COMPANY))
    }

    @Test
    fun isActiveOnlyForActiveStatus() {
        assertTrue(AdminAccessOps.isActive(active))
        assertFalse(AdminAccessOps.isActive(expired))
    }

    @Test
    fun brandedConfigHasNonBlankBrandName() {
        assertTrue(AdminAccessOps.isBranded(WhiteLabelConfig("b1", brandName = "Ali Autos")))
        assertFalse(AdminAccessOps.isBranded(WhiteLabelConfig("b1")))
    }

    @Test
    fun whiteLabelConfigCarriesBrandAndColor() {
        val config = WhiteLabelConfig("b1", brandName = "Ali Autos", primaryColor = "#0057B8")
        assertEquals("Ali Autos", config.brandName)
        assertEquals("#0057B8", config.primaryColor)
    }
}
