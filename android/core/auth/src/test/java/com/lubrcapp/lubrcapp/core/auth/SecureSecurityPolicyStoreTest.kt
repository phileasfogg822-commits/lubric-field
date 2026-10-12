package com.lubrcapp.lubrcapp.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSecurityPolicyStoreTest {

    @Test
    fun securePolicyDefaultsToStrongLocalProtection() = runBlocking {
        val store = SecureSecurityPolicyStore(FakeSecureAuthStorage())

        store.initialize()

        assertTrue(store.policy.value.blockScreenshots)
        assertTrue(store.policy.value.lockOnBackground)
    }

    @Test
    fun policyChangesPersistThroughSecureStorage() = runBlocking {
        val storage = FakeSecureAuthStorage()
        val first = SecureSecurityPolicyStore(storage)

        first.initialize()
        first.setBlockScreenshots(false)
        first.setLockOnBackground(false)

        val restored = SecureSecurityPolicyStore(storage)
        restored.initialize()

        assertFalse(restored.policy.value.blockScreenshots)
        assertFalse(restored.policy.value.lockOnBackground)
    }

    private class FakeSecureAuthStorage : SecureAuthStorage {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key)
                else this.values[key] = value
            }
        }

        override fun clear() {
            values.clear()
        }
    }
}
