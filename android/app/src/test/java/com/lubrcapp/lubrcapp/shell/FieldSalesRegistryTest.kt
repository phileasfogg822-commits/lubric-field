package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSalesRegistryTest {

    @Test
    fun fieldSalesEntryIsGatedSelectableAndRenders() {
        val entry = AppModuleRegistry.module(AppModuleRegistry.FIELD_SALES_ID)
        assertNotNull(entry)
        assertEquals(true, entry!!.selectable)
        assertEquals(FeatureFlagKeys.MODULE_ORDERS, entry.configKey)
        assertEquals(Capabilities.SALES_VIEW, entry.viewCapability)
        assertNotNull(entry.content)
        assertNull(entry.bottomNavSlot)
    }

    @Test
    fun fieldSalesHidesWithoutItsConfigKeyOrCapability() {
        val entry = AppModuleRegistry.module(AppModuleRegistry.FIELD_SALES_ID)!!
        assertFalse(AppModuleRegistry.isVisible(entry, emptySet(), Capabilities.ALL))
        assertFalse(AppModuleRegistry.isVisible(entry, FeatureFlagKeys.ALL, emptySet()))
        assertTrue(AppModuleRegistry.isVisible(entry, FeatureFlagKeys.ALL, Capabilities.ALL))
    }
}
