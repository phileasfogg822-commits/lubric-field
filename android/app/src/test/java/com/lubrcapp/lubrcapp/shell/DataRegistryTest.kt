package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRegistryTest {

    @Test
    fun dataEntryIsGatedSelectableAndRenders() {
        val data = AppModuleRegistry.module(AppModuleRegistry.DATA_ID)
        assertNotNull(data)
        assertEquals(true, data!!.selectable)
        assertEquals(FeatureFlagKeys.MODULE_BACKUP, data.configKey)
        assertEquals(Capabilities.DATA_VIEW, data.viewCapability)
        assertNotNull(data.content)
        assertNull(data.bottomNavSlot)
    }

    @Test
    fun dataHidesWithoutItsConfigKeyOrCapability() {
        val data = AppModuleRegistry.module(AppModuleRegistry.DATA_ID)!!
        assertFalse(AppModuleRegistry.isVisible(data, emptySet(), Capabilities.ALL))
        assertFalse(AppModuleRegistry.isVisible(data, FeatureFlagKeys.ALL, emptySet()))
        assertTrue(AppModuleRegistry.isVisible(data, FeatureFlagKeys.ALL, Capabilities.ALL))
    }
}
