package com.lubrcapp.lubrcapp.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val localGateStore: LocalAuthGateStore,
    private val remote: AuthRemoteDataSource,
    private val clock: AuthClock,
) : AuthSessionController {

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Initializing)

    override val state: StateFlow<AuthState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            mutableState.value = restoreState(respectLock = true)
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: CharArray,
    ) {
        require(email.isNotBlank()) { "email cannot be blank" }
        require(password.isNotEmpty()) { "password cannot be empty" }

        mutex.withLock {
            val previous = mutableState.value
            mutableState.value = AuthState.Authenticating(AuthMethod.EMAIL_PASSWORD)

            val result = try {
                remote.signInWithEmail(email.trim(), password)
            } finally {
                password.fill('\u0000')
            }
            handleAuthenticationResult(result, previous)
        }
    }

    override suspend fun requestOtp(
        phone: String,
    ): AuthRemoteResult<OtpChallenge> {
        require(phone.isNotBlank()) { "phone cannot be blank" }
        return remote.requestOtp(phone.trim())
    }

    override suspend fun verifyOtp(
        challengeId: String,
        otp: CharArray,
    ) {
        require(challengeId.isNotBlank()) { "challengeId cannot be blank" }
        require(otp.isNotEmpty()) { "otp cannot be empty" }

        mutex.withLock {
            val previous = mutableState.value
            mutableState.value = AuthState.Authenticating(AuthMethod.PHONE_OTP)
            val result = try {
                remote.verifyOtp(challengeId, otp)
            } finally {
                otp.fill('\u0000')
            }
            handleAuthenticationResult(result, previous)
        }
    }

    override suspend fun refresh() {
        mutex.withLock {
            val session = tokenStore.read()
            if (session == null) {
                mutableState.value = AuthState.Unauthenticated()
                return
            }

            val now = clock.now()
            if (session.tokens.refreshTokenExpiresAt <= now) {
                expireLocalSession()
                return
            }

            val previous = mutableState.value
            mutableState.value = AuthState.Refreshing(session.userId)

            when (val result = remote.refresh(session.tokens.refreshToken)) {
                is AuthRemoteResult.Success -> {
                    if (!isUsable(result.value)) {
                        expireLocalSession()
                        return
                    }
                    tokenStore.write(result.value)
                    mutableState.value = stateForSession(
                        result.value,
                        respectLock = true,
                    )
                }

                is AuthRemoteResult.InvalidCredentials -> {
                    clearLocalSession()
                    mutableState.value = AuthState.Unauthenticated(
                        SignOutReason.CREDENTIALS_REVOKED
                    )
                }

                is AuthRemoteResult.Unavailable -> {
                    mutableState.value = if (session.tokens.offlineAccessUntil > now) {
                        stateForSession(session, respectLock = true)
                    } else {
                        AuthState.Error(
                            message = result.reason,
                            retryable = true,
                            previous = previous,
                        )
                    }
                }

                is AuthRemoteResult.Failure -> {
                    mutableState.value = if (
                        result.retryable &&
                        session.tokens.offlineAccessUntil > now
                    ) {
                        stateForSession(session, respectLock = true)
                    } else {
                        AuthState.Error(
                            message = result.message,
                            retryable = result.retryable,
                            previous = previous,
                        )
                    }
                }
            }
        }
    }

    override suspend fun securityInfo(): SessionSecurityInfo? =
        mutex.withLock {
            tokenStore.read()?.let { session ->
                SessionSecurityInfo(
                    sessionId = session.sessionId,
                    userId = session.userId,
                    issuedAt = session.issuedAt,
                    accessTokenExpiresAt = session.tokens.accessTokenExpiresAt,
                    refreshTokenExpiresAt = session.tokens.refreshTokenExpiresAt,
                    offlineAccessUntil = session.tokens.offlineAccessUntil,
                )
            }
        }

    override suspend fun logout() {
        val refreshToken = mutex.withLock {
            val token = tokenStore.read()?.tokens?.refreshToken
            clearLocalSession()
            mutableState.value = AuthState.Unauthenticated(SignOutReason.USER_REQUESTED)
            token
        }

        if (!refreshToken.isNullOrBlank()) {
            runCatching { remote.revoke(refreshToken) }
        }
    }

    override suspend fun lock() {
        mutex.withLock {
            val session = tokenStore.read()
            if (session == null) {
                mutableState.value = AuthState.Unauthenticated()
                return
            }
            val pinAvailable = localGateStore.hasPin()
            val biometricAvailable = localGateStore.isBiometricEnabled()
            if (!pinAvailable && !biometricAvailable) {
                localGateStore.setLocked(false)
                mutableState.value = stateForSession(session, respectLock = false)
                return@withLock
            }
            localGateStore.setLocked(true)
            mutableState.value = AuthState.Locked(
                userId = session.userId,
                pinAvailable = pinAvailable,
                biometricAvailable = biometricAvailable,
            )
        }
    }

    override suspend fun unlockWithPin(pin: CharArray): Boolean =
        mutex.withLock {
            val verified = localGateStore.verifyPin(pin)
            if (!verified) return@withLock false

            val session = tokenStore.read()
            if (session == null || !isUsable(session)) {
                expireLocalSession()
                return@withLock false
            }

            localGateStore.setLocked(false)
            mutableState.value = stateForSession(session, respectLock = false)
            true
        }

    override suspend fun unlockAfterBiometricSuccess(): Boolean =
        mutex.withLock {
            if (!localGateStore.isBiometricEnabled()) return@withLock false

            val session = tokenStore.read()
            if (session == null || !isUsable(session)) {
                expireLocalSession()
                return@withLock false
            }

            localGateStore.setLocked(false)
            mutableState.value = stateForSession(session, respectLock = false)
            true
        }

    override suspend fun setPin(pin: CharArray) {
        localGateStore.setPin(pin)
    }

    override suspend fun clearPin() {
        localGateStore.clearPin()
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        localGateStore.setBiometricEnabled(enabled)
    }

    override suspend fun freshAccessTokenOrNull(): String? =
        mutex.withLock {
            if (localGateStore.isLocked()) return@withLock null
            val session = tokenStore.read() ?: return@withLock null
            if (session.tokens.accessTokenExpiresAt <= clock.now()) return@withLock null
            session.tokens.accessToken
        }

    private suspend fun handleAuthenticationResult(
        result: AuthRemoteResult<AuthSession>,
        previous: AuthState,
    ) {
        when (result) {
            is AuthRemoteResult.Success -> {
                if (!isUsable(result.value)) {
                    mutableState.value = AuthState.Error(
                        message = "Authentication service returned an expired session",
                        retryable = false,
                        previous = previous,
                    )
                    return
                }
                tokenStore.write(result.value)
                localGateStore.setLocked(false)
                mutableState.value = stateForSession(
                    session = result.value,
                    respectLock = false,
                )
            }

            is AuthRemoteResult.InvalidCredentials -> {
                mutableState.value = AuthState.Error(
                    message = result.message,
                    retryable = false,
                    previous = AuthState.Unauthenticated(),
                )
            }

            is AuthRemoteResult.Unavailable -> {
                mutableState.value = AuthState.Error(
                    message = result.reason,
                    retryable = true,
                    previous = previous,
                )
            }

            is AuthRemoteResult.Failure -> {
                mutableState.value = AuthState.Error(
                    message = result.message,
                    retryable = result.retryable,
                    previous = previous,
                )
            }
        }
    }

    private suspend fun restoreState(
        respectLock: Boolean,
    ): AuthState {
        val session = tokenStore.read() ?: return AuthState.Unauthenticated()
        if (!isUsable(session)) {
            expireLocalSession()
            return AuthState.Unauthenticated(SignOutReason.SESSION_EXPIRED)
        }
        return stateForSession(session, respectLock)
    }

    private suspend fun stateForSession(
        session: AuthSession,
        respectLock: Boolean,
    ): AuthState {
        if (respectLock && localGateStore.isLocked()) {
            return lockedState(session)
        }

        val freshness = if (session.tokens.accessTokenExpiresAt > clock.now()) {
            SessionFreshness.FRESH
        } else {
            SessionFreshness.OFFLINE_GRACE
        }
        return AuthState.Authenticated(
            userId = session.userId,
            freshness = freshness,
        )
    }

    private suspend fun lockedState(session: AuthSession): AuthState.Locked =
        AuthState.Locked(
            userId = session.userId,
            pinAvailable = localGateStore.hasPin(),
            biometricAvailable = localGateStore.isBiometricEnabled(),
        )

    private fun isUsable(session: AuthSession): Boolean {
        val now = clock.now()
        return session.tokens.refreshTokenExpiresAt > now &&
            (
                session.tokens.accessTokenExpiresAt > now ||
                    session.tokens.offlineAccessUntil > now
                )
    }

    private suspend fun expireLocalSession() {
        clearLocalSession()
        mutableState.value = AuthState.Unauthenticated(SignOutReason.SESSION_EXPIRED)
    }

    private suspend fun clearLocalSession() {
        tokenStore.clear()
        localGateStore.setLocked(false)
        localGateStore.clearPin()
        localGateStore.setBiometricEnabled(false)
    }
}
