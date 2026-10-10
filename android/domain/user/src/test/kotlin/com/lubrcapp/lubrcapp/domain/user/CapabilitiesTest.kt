package com.lubrcapp.lubrcapp.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    @Test
    fun catalogKeysAreUniqueAndStableIdentifiers() {
        val keys = Capabilities.DEFINITIONS.map { it.key.value }

        assertEquals(keys.size, keys.toSet().size)
        assertEquals(Capabilities.ALL, Capabilities.DEFINITIONS.mapTo(linkedSetOf()) { it.key })
        assertTrue(keys.all { it.matches(Regex("[a-z][a-z0-9_.-]*")) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCapabilityKeyIsRejected() {
        CapabilityKey("Admin Users")
    }
}
