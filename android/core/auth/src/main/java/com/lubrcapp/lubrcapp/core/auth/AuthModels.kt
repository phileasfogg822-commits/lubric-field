package com.lubrcapp.lubrcapp.core.auth

import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant

enum class AuthMethod {
    EMAIL_PASSWORD,
    PHONE_OTP,
    PIN,
    BIOMETRIC,
}

enum class SessionFreshness {
    FRESH,
    OFFLINE_GRACE,
}

enum class SignOutReason {
    USER_REQUESTED,
    SESSION_EXPIRED,
    CREDENTIALS_REVOKED,
    STORAGE_UNAVAILABLE,
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val offlineAccessUntil: Instant,
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken cannot be blank" }
        require(refreshToken.isNotBlank()) { "refreshToken cannot be blank" }
        require(accessTokenExpiresAt <= refreshTokenExpiresAt) {
            "access token cannot outlive refresh token"
        }
        require(offlineAccessUntil <= refreshTokenExpiresAt) {
            "offline access cannot outlive refresh token"
        }
    }
}

data class AuthSession(
    val sessionId: String,
    val userId: UserId,
    val issuedAt: Instant,
    val tokens: AuthTokens,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId cannot be blank" }
    }
}

data class SessionSecurityInfo(
    val sessionId: String,
    val userId: UserId,
    val issuedAt: Instant,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val offlineAccessUntil: Instant,
)

data class OtpChallenge(
    val challengeId: String,
    val destinationHint: String,
    val expiresAt: Instant,
) {
    init {
        require(challengeId.isNotBlank()) { "challengeId cannot be blank" }
    }
}

sealed interface AuthRemoteResult<out T> {
    data class Success<T>(val value: T) : AuthRemoteResult<T>
    data class InvalidCredentials(val message: String = "Invalid credentials") :
        AuthRemoteResult<Nothing>
    data class Unavailable(val reason: String) : AuthRemoteResult<Nothing>
    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : AuthRemoteResult<Nothing>
}

sealed interface AuthState {
    data object Initializing : AuthState

    data class Unauthenticated(
        val reason: SignOutReason? = null,
    ) : AuthState

    data class Authenticating(
        val method: AuthMethod,
    ) : AuthState

    data class Refreshing(
        val userId: UserId,
    ) : AuthState

    data class Authenticated(
        val userId: UserId,
        val freshness: SessionFreshness,
    ) : AuthState

    data class Locked(
        val userId: UserId,
        val pinAvailable: Boolean,
        val biometricAvailable: Boolean,
    ) : AuthState

    data class Error(
        val message: String,
        val retryable: Boolean,
        val previous: AuthState,
    ) : AuthState
}

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data class Failure(val message: String) : BiometricResult
}

enum class BiometricAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    TEMPORARILY_UNAVAILABLE,
    UNSUPPORTED,
}
