package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosRegistryTest {

    @Test
    fun posEntryIsGatedSelectableAndRenders() {
        val pos = AppModuleRegistry.module(AppModuleRegistry.POS_ID)
        assertNotNull(pos)
        assertEquals(true, pos!!.selectable)
        assertEquals(FeatureFlagKeys.MODULE_ORDERS, pos.configKey)
        assertEquals(Capabilities.SALES_VIEW, pos.viewCapability)
        assertNotNull(pos.content)
        assertNull(pos.bottomNavSlot)
    }

    @Test
    fun posHidesWithoutItsConfigKeyOrCapability() {
        val pos = AppModuleRegistry.module(AppModuleRegistry.POS_ID)!!
        assertFalse(AppModuleRegistry.isVisible(pos, emptySet(), Capabilities.ALL))
        assertFalse(AppModuleRegistry.isVisible(pos, FeatureFlagKeys.ALL, emptySet()))
        assertTrue(AppModuleRegistry.isVisible(pos, FeatureFlagKeys.ALL, Capabilities.ALL))
    }
}
