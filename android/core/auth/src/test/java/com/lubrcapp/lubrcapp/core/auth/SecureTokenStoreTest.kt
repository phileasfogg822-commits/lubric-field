package com.lubrcapp.lubrcapp.core.auth

import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureTokenStoreTest {

    @Test
    fun sessionRoundTripsWithoutPlainObjectPersistence() = runBlocking {
        val storage = FakeSecureAuthStorage()
        val store = SecureTokenStore(storage)
        val session = session()

        store.write(session)

        assertEquals(session, store.read())
        assertEquals(session.tokens.accessToken, storage.values["session.access_token"])
        assertEquals(session.tokens.refreshToken, storage.values["session.refresh_token"])
    }

    @Test
    fun clearRemovesOnlySessionKeys() = runBlocking {
        val storage = FakeSecureAuthStorage().apply {
            values["gate.pin_hash"] = "keep-me"
        }
        val store = SecureTokenStore(storage)
        store.write(session())

        store.clear()

        assertNull(store.read())
        assertEquals("keep-me", storage.values["gate.pin_hash"])
    }

    private fun session() = AuthSession(
        sessionId = "session-1",
        userId = UserId("user-1"),
        issuedAt = Instant.parse("2026-10-08T10:00:00Z"),
        tokens = AuthTokens(
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
            accessTokenExpiresAt = Instant.parse("2026-10-08T11:00:00Z"),
            refreshTokenExpiresAt = Instant.parse("2026-10-15T10:00:00Z"),
            offlineAccessUntil = Instant.parse("2026-10-10T10:00:00Z"),
        ),
    )

    private class FakeSecureAuthStorage : SecureAuthStorage {
        val values = mutableMapOf<String, String>()

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
