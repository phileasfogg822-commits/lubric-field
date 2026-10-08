package com.lubrcapp.lubrcapp.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureLocalAuthGateStoreTest {

    @Test
    fun pinBiometricAndLockStatePersistThroughSecureStorage() = runBlocking {
        val storage = FakeSecureAuthStorage()
        val store = SecureLocalAuthGateStore(storage, PinHasher())
        val pin = "2468".toCharArray()

        store.setPin(pin)
        store.setBiometricEnabled(true)
        store.setLocked(true)

        assertTrue(pin.all { it == '\u0000' })
        assertTrue(store.hasPin())
        assertTrue(store.verifyPin("2468".toCharArray()))
        assertFalse(store.verifyPin("1357".toCharArray()))
        assertTrue(store.isBiometricEnabled())
        assertTrue(store.isLocked())

        store.clearPin()
        store.setBiometricEnabled(false)
        store.setLocked(false)

        assertFalse(store.hasPin())
        assertFalse(store.isBiometricEnabled())
        assertFalse(store.isLocked())
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
