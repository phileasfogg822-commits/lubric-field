package com.lubrcapp.lubrcapp.core.auth

import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
    private val now = Instant.parse("2026-10-08T10:00:00Z")

    @Test
    fun initializeRestoresFreshSession() = runBlocking {
        val tokenStore = FakeTokenStore(freshSession())
        val manager = manager(tokenStore = tokenStore)

        manager.initialize()

        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.FRESH),
            manager.state.value,
        )
        assertEquals("access-new", manager.freshAccessTokenOrNull())
    }

    @Test
    fun initializeUsesOfflineGraceWhenAccessTokenExpired() = runBlocking {
        val session = freshSession().copy(
            tokens = freshSession().tokens.copy(
                accessTokenExpiresAt = now.minusSeconds(1),
            )
        )
        val manager = manager(tokenStore = FakeTokenStore(session))

        manager.initialize()

        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.OFFLINE_GRACE),
            manager.state.value,
        )
        assertNull(manager.freshAccessTokenOrNull())
    }

    @Test
    fun expiredRefreshWindowClearsLocalSession() = runBlocking {
        val tokenStore = FakeTokenStore(
            freshSession().copy(
                tokens = freshSession().tokens.copy(
                    accessTokenExpiresAt = now.minusSeconds(20),
                    offlineAccessUntil = now.minusSeconds(10),
                    refreshTokenExpiresAt = now.minusSeconds(1),
                )
            )
        )
        val gate = FakeGateStore(pinConfigured = true, biometricEnabled = true)
        val manager = manager(tokenStore, gate = gate)

        manager.initialize()

        assertEquals(
            AuthState.Unauthenticated(SignOutReason.SESSION_EXPIRED),
            manager.state.value,
        )
        assertNull(tokenStore.session)
        assertFalse(gate.pinConfigured)
        assertFalse(gate.biometricEnabled)
    }

    @Test
    fun refreshUnavailableFallsBackToOfflineGrace() = runBlocking {
        val session = freshSession().copy(
            tokens = freshSession().tokens.copy(
                accessTokenExpiresAt = now.minusSeconds(1),
            )
        )
        val remote = FakeRemote().apply {
            refreshResult = AuthRemoteResult.Unavailable("offline")
        }
        val manager = manager(
            tokenStore = FakeTokenStore(session),
            remote = remote,
        )

        manager.refresh()

        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.OFFLINE_GRACE),
            manager.state.value,
        )
    }

    @Test
    fun successfulRefreshPersistsReplacementSession() = runBlocking {
        val old = freshSession().copy(
            tokens = freshSession().tokens.copy(
                accessTokenExpiresAt = now.minusSeconds(1),
            )
        )
        val replacement = freshSession().copy(
            sessionId = "session-2",
            tokens = freshSession().tokens.copy(
                accessToken = "replacement-access",
                refreshToken = "replacement-refresh",
            ),
        )
        val store = FakeTokenStore(old)
        val remote = FakeRemote().apply {
            refreshResult = AuthRemoteResult.Success(replacement)
        }
        val manager = manager(store, remote = remote)

        manager.refresh()

        assertEquals(replacement, store.session)
        assertEquals("replacement-access", manager.freshAccessTokenOrNull())
        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.FRESH),
            manager.state.value,
        )
    }

    @Test
    fun lockBlocksTokenAndPinUnlockRestoresSession() = runBlocking {
        val gate = FakeGateStore(pinConfigured = true).apply {
            acceptedPin = "2468"
        }
        val manager = manager(
            tokenStore = FakeTokenStore(freshSession()),
            gate = gate,
        )
        manager.initialize()

        manager.lock()

        assertTrue(manager.state.value is AuthState.Locked)
        assertNull(manager.freshAccessTokenOrNull())

        assertTrue(manager.unlockWithPin("2468".toCharArray()))
        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.FRESH),
            manager.state.value,
        )
        assertEquals("access-new", manager.freshAccessTokenOrNull())
    }


    @Test
    fun lockWithoutUnlockMethodDoesNotTrapSession() = runBlocking {
        val manager = manager(
            tokenStore = FakeTokenStore(freshSession()),
            gate = FakeGateStore(),
        )
        manager.initialize()

        manager.lock()

        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.FRESH),
            manager.state.value,
        )
        assertEquals("access-new", manager.freshAccessTokenOrNull())
    }

    @Test
    fun securityInfoExposesMetadataWithoutTokenMaterial() = runBlocking {
        val manager = manager(tokenStore = FakeTokenStore(freshSession()))

        val info = manager.securityInfo()

        assertNotNull(info)
        assertEquals("session-1", info?.sessionId)
        assertEquals(UserId("user-1"), info?.userId)
        assertEquals(freshSession().tokens.offlineAccessUntil, info?.offlineAccessUntil)
        assertFalse(info.toString().contains("access-new"))
        assertFalse(info.toString().contains("refresh-new"))
    }

    @Test
    fun logoutClearsSessionUnlockMethodsAndAttemptsRevoke() = runBlocking {
        val store = FakeTokenStore(freshSession())
        val gate = FakeGateStore(
            pinConfigured = true,
            biometricEnabled = true,
            locked = true,
        )
        val remote = FakeRemote()
        val manager = manager(store, gate, remote)

        manager.logout()

        assertNull(store.session)
        assertFalse(gate.pinConfigured)
        assertFalse(gate.biometricEnabled)
        assertFalse(gate.locked)
        assertEquals("refresh-new", remote.revokedRefreshToken)
        assertEquals(
            AuthState.Unauthenticated(SignOutReason.USER_REQUESTED),
            manager.state.value,
        )
    }

    @Test
    fun signInClearsCallerPasswordBuffer() = runBlocking {
        val remote = FakeRemote().apply {
            signInResult = AuthRemoteResult.Success(freshSession())
        }
        val manager = manager(
            tokenStore = FakeTokenStore(),
            remote = remote,
        )
        val password = "secret-password".toCharArray()

        manager.signInWithEmail("user@example.com", password)

        assertTrue(password.all { it == '\u0000' })
        assertEquals(
            AuthState.Authenticated(UserId("user-1"), SessionFreshness.FRESH),
            manager.state.value,
        )
    }


    @Test
    fun revokedCredentialsDuringRefreshClearTokensAndBlockAccess() = runBlocking {
        val store = FakeTokenStore(freshSession())
        val remote = FakeRemote().apply {
            refreshResult = AuthRemoteResult.InvalidCredentials("revoked")
        }
        val manager = manager(store, remote = remote)
        manager.initialize()
        assertEquals("access-new", manager.freshAccessTokenOrNull())

        manager.refresh()

        assertEquals(
            AuthState.Unauthenticated(SignOutReason.CREDENTIALS_REVOKED),
            manager.state.value,
        )
        assertNull(store.session)
        assertNull(manager.freshAccessTokenOrNull())
        assertNull(manager.securityInfo())
    }

    @Test
    fun expiredAccessTokenIsNeverReturnedDuringOfflineGrace() = runBlocking {
        val session = freshSession().copy(
            tokens = freshSession().tokens.copy(
                accessTokenExpiresAt = now.minusSeconds(1),
            ),
        )
        val manager = manager(FakeTokenStore(session))
        manager.initialize()
        assertTrue(manager.state.value is AuthState.Authenticated)
        assertNull(manager.freshAccessTokenOrNull())
    }

    @Test
    fun expiredRefreshTokenBlocksRefreshAndClearsStoredSession() = runBlocking {
        val session = freshSession().copy(
            tokens = freshSession().tokens.copy(
                accessTokenExpiresAt = now.minusSeconds(30),
                offlineAccessUntil = now.minusSeconds(20),
                refreshTokenExpiresAt = now.minusSeconds(10),
            ),
        )
        val store = FakeTokenStore(session)
        val manager = manager(store)
        manager.refresh()
        assertEquals(
            AuthState.Unauthenticated(SignOutReason.SESSION_EXPIRED),
            manager.state.value,
        )
        assertNull(store.session)
        assertNull(manager.freshAccessTokenOrNull())
    }


    @Test
    fun authModelsDoNotExposeCredentialValuesInDiagnosticStrings() {
        val session = freshSession()
        val tokensText = session.tokens.toString()
        val sessionText = session.toString()
        listOf(tokensText, sessionText).forEach { diagnostic ->
            assertFalse(diagnostic.contains("access-new"))
            assertFalse(diagnostic.contains("refresh-new"))
            assertTrue(diagnostic.contains("[REDACTED]"))
        }
    }


    @Test
    fun authenticationErrorDiagnosticsDoNotContainCredentials() = runBlocking {
        val remote = FakeRemote().apply {
            signInResult = AuthRemoteResult.InvalidCredentials()
        }
        val manager = manager(remote = remote)
        val password = "diagnostic-secret-marker".toCharArray()
        manager.signInWithEmail("user@example.com", password)
        val diagnostic = manager.state.value.toString()
        assertFalse(diagnostic.contains("diagnostic-secret-marker"))
        assertFalse(diagnostic.contains("access-new"))
        assertFalse(diagnostic.contains("refresh-new"))
    }

    private fun manager(
        tokenStore: FakeTokenStore = FakeTokenStore(),
        gate: FakeGateStore = FakeGateStore(),
        remote: FakeRemote = FakeRemote(),
    ) = SessionManager(
        tokenStore = tokenStore,
        localGateStore = gate,
        remote = remote,
        clock = AuthClock { now },
    )

    private fun freshSession() = AuthSession(
        sessionId = "session-1",
        userId = UserId("user-1"),
        issuedAt = now.minusSeconds(300),
        tokens = AuthTokens(
            accessToken = "access-new",
            refreshToken = "refresh-new",
            accessTokenExpiresAt = now.plusSeconds(3600),
            refreshTokenExpiresAt = now.plusSeconds(604800),
            offlineAccessUntil = now.plusSeconds(172800),
        ),
    )

    private class FakeTokenStore(
        var session: AuthSession? = null,
    ) : TokenStore {
        override suspend fun read(): AuthSession? = session
        override suspend fun write(session: AuthSession) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeGateStore(
        var pinConfigured: Boolean = false,
        var biometricEnabled: Boolean = false,
        var locked: Boolean = false,
    ) : LocalAuthGateStore {
        var acceptedPin: String = "2468"

        override suspend fun hasPin(): Boolean = pinConfigured

        override suspend fun setPin(pin: CharArray) {
            pinConfigured = true
            acceptedPin = String(pin)
            pin.fill('\u0000')
        }

        override suspend fun verifyPin(pin: CharArray): Boolean {
            val matches = pinConfigured && String(pin) == acceptedPin
            pin.fill('\u0000')
            return matches
        }

        override suspend fun clearPin() {
            pinConfigured = false
        }

        override suspend fun isBiometricEnabled(): Boolean = biometricEnabled

        override suspend fun setBiometricEnabled(enabled: Boolean) {
            biometricEnabled = enabled
        }

        override suspend fun isLocked(): Boolean = locked

        override suspend fun setLocked(locked: Boolean) {
            this.locked = locked
        }
    }

    private class FakeRemote : AuthRemoteDataSource {
        var signInResult: AuthRemoteResult<AuthSession> =
            AuthRemoteResult.Unavailable("not configured")
        var refreshResult: AuthRemoteResult<AuthSession> =
            AuthRemoteResult.Unavailable("not configured")
        var revokedRefreshToken: String? = null

        override suspend fun signInWithEmail(
            email: String,
            password: CharArray,
        ): AuthRemoteResult<AuthSession> = signInResult

        override suspend fun requestOtp(
            phone: String,
        ): AuthRemoteResult<OtpChallenge> =
            AuthRemoteResult.Unavailable("not configured")

        override suspend fun verifyOtp(
            challengeId: String,
            otp: CharArray,
        ): AuthRemoteResult<AuthSession> =
            AuthRemoteResult.Unavailable("not configured")

        override suspend fun refresh(
            refreshToken: String,
        ): AuthRemoteResult<AuthSession> = refreshResult

        override suspend fun revoke(
            refreshToken: String,
        ): AuthRemoteResult<Unit> {
            revokedRefreshToken = refreshToken
            return AuthRemoteResult.Success(Unit)
        }
    }
}
