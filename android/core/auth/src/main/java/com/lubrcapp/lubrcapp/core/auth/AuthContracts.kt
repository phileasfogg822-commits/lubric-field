package com.lubrcapp.lubrcapp.core.auth

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

interface AuthRemoteDataSource {
    suspend fun signInWithEmail(
        email: String,
        password: CharArray,
    ): AuthRemoteResult<AuthSession>

    suspend fun requestOtp(
        phone: String,
    ): AuthRemoteResult<OtpChallenge>

    suspend fun verifyOtp(
        challengeId: String,
        otp: CharArray,
    ): AuthRemoteResult<AuthSession>

    suspend fun refresh(
        refreshToken: String,
    ): AuthRemoteResult<AuthSession>

    suspend fun revoke(
        refreshToken: String,
    ): AuthRemoteResult<Unit>
}

interface TokenStore {
    suspend fun read(): AuthSession?
    suspend fun write(session: AuthSession)
    suspend fun clear()
}

interface LocalAuthGateStore {
    suspend fun hasPin(): Boolean
    suspend fun setPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): Boolean
    suspend fun clearPin()

    suspend fun isBiometricEnabled(): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)

    suspend fun isLocked(): Boolean
    suspend fun setLocked(locked: Boolean)
}

fun interface AuthClock {
    fun now(): Instant
}

interface SessionAccessTokenProvider {
    suspend fun freshAccessTokenOrNull(): String?
}

interface AuthSessionController : SessionAccessTokenProvider {
    val state: StateFlow<AuthState>

    suspend fun initialize()
    suspend fun signInWithEmail(email: String, password: CharArray)
    suspend fun requestOtp(phone: String): AuthRemoteResult<OtpChallenge>
    suspend fun verifyOtp(challengeId: String, otp: CharArray)
    suspend fun refresh()
    suspend fun logout()
    suspend fun lock()
    suspend fun unlockWithPin(pin: CharArray): Boolean
    suspend fun unlockAfterBiometricSuccess(): Boolean
    suspend fun setPin(pin: CharArray)
    suspend fun clearPin()
    suspend fun setBiometricEnabled(enabled: Boolean)
}
