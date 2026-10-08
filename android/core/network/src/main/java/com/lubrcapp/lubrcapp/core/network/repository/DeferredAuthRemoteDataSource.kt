package com.lubrcapp.lubrcapp.core.network.repository

import com.lubrcapp.lubrcapp.core.auth.AuthRemoteDataSource
import com.lubrcapp.lubrcapp.core.auth.AuthRemoteResult
import com.lubrcapp.lubrcapp.core.auth.AuthSession
import com.lubrcapp.lubrcapp.core.auth.OtpChallenge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-7 remote authentication boundary.
 *
 * The Android session architecture is complete, but concrete login/refresh
 * endpoints are intentionally deferred until the server API contract exists.
 */
@Singleton
class DeferredAuthRemoteDataSource @Inject constructor() : AuthRemoteDataSource {
    private fun <T> unavailable(): AuthRemoteResult<T> =
        AuthRemoteResult.Unavailable("Remote authentication API is not configured yet")

    override suspend fun signInWithEmail(
        email: String,
        password: CharArray,
    ): AuthRemoteResult<AuthSession> = unavailable()

    override suspend fun requestOtp(
        phone: String,
    ): AuthRemoteResult<OtpChallenge> = unavailable()

    override suspend fun verifyOtp(
        challengeId: String,
        otp: CharArray,
    ): AuthRemoteResult<AuthSession> = unavailable()

    override suspend fun refresh(
        refreshToken: String,
    ): AuthRemoteResult<AuthSession> = unavailable()

    override suspend fun revoke(
        refreshToken: String,
    ): AuthRemoteResult<Unit> = unavailable()
}
