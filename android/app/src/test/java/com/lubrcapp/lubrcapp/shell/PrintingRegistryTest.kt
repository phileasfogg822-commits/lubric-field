package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintingRegistryTest {

    @Test
    fun printingEntryIsGatedSelectableAndRenders() {
        val printing = AppModuleRegistry.module(AppModuleRegistry.PRINTING_ID)
        assertNotNull(printing)
        assertEquals(true, printing!!.selectable)
        assertEquals(FeatureFlagKeys.MODULE_PRINTING, printing.configKey)
        assertEquals(Capabilities.PRINTING_VIEW, printing.viewCapability)
        assertNotNull(printing.content)
        assertNull(printing.bottomNavSlot)
    }

    @Test
    fun printingHidesWithoutItsConfigKeyOrCapability() {
        val printing = AppModuleRegistry.module(AppModuleRegistry.PRINTING_ID)!!
        assertFalse(AppModuleRegistry.isVisible(printing, emptySet(), Capabilities.ALL))
        assertFalse(AppModuleRegistry.isVisible(printing, FeatureFlagKeys.ALL, emptySet()))
        assertTrue(AppModuleRegistry.isVisible(printing, FeatureFlagKeys.ALL, Capabilities.ALL))
    }
}
