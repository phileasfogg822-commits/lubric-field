package com.lubrcapp.lubrcapp.core.auth

import com.lubrcapp.lubrcapp.domain.common.UserId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SecureTokenStore @Inject constructor(
    private val storage: SecureAuthStorage,
) : TokenStore {

    override suspend fun read(): AuthSession? = withContext(Dispatchers.IO) {
        val sessionId = storage.read(KEY_SESSION_ID) ?: return@withContext null
        val userId = storage.read(KEY_USER_ID) ?: return@withContext null
        val issuedAt = storage.readInstant(KEY_ISSUED_AT) ?: return@withContext null
        val accessToken = storage.read(KEY_ACCESS_TOKEN) ?: return@withContext null
        val refreshToken = storage.read(KEY_REFRESH_TOKEN) ?: return@withContext null
        val accessExpiresAt = storage.readInstant(KEY_ACCESS_EXPIRES_AT)
            ?: return@withContext null
        val refreshExpiresAt = storage.readInstant(KEY_REFRESH_EXPIRES_AT)
            ?: return@withContext null
        val offlineAccessUntil = storage.readInstant(KEY_OFFLINE_ACCESS_UNTIL)
            ?: return@withContext null

        runCatching {
            AuthSession(
                sessionId = sessionId,
                userId = UserId(userId),
                issuedAt = issuedAt,
                tokens = AuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accessTokenExpiresAt = accessExpiresAt,
                    refreshTokenExpiresAt = refreshExpiresAt,
                    offlineAccessUntil = offlineAccessUntil,
                ),
            )
        }.getOrNull()
    }

    override suspend fun write(session: AuthSession) = withContext(Dispatchers.IO) {
        storage.write(
            mapOf(
                KEY_SESSION_ID to session.sessionId,
                KEY_USER_ID to session.userId.value,
                KEY_ISSUED_AT to session.issuedAt.toEpochMilli().toString(),
                KEY_ACCESS_TOKEN to session.tokens.accessToken,
                KEY_REFRESH_TOKEN to session.tokens.refreshToken,
                KEY_ACCESS_EXPIRES_AT to session.tokens.accessTokenExpiresAt.toEpochMilli().toString(),
                KEY_REFRESH_EXPIRES_AT to session.tokens.refreshTokenExpiresAt.toEpochMilli().toString(),
                KEY_OFFLINE_ACCESS_UNTIL to session.tokens.offlineAccessUntil.toEpochMilli().toString(),
            )
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        storage.write(
            mapOf(
                KEY_SESSION_ID to null,
                KEY_USER_ID to null,
                KEY_ISSUED_AT to null,
                KEY_ACCESS_TOKEN to null,
                KEY_REFRESH_TOKEN to null,
                KEY_ACCESS_EXPIRES_AT to null,
                KEY_REFRESH_EXPIRES_AT to null,
                KEY_OFFLINE_ACCESS_UNTIL to null,
            )
        )
    }

    private fun SecureAuthStorage.readInstant(key: String): Instant? =
        read(key)?.toLongOrNull()?.let(Instant::ofEpochMilli)

    private companion object {
        const val KEY_SESSION_ID = "session.id"
        const val KEY_USER_ID = "session.user_id"
        const val KEY_ISSUED_AT = "session.issued_at"
        const val KEY_ACCESS_TOKEN = "session.access_token"
        const val KEY_REFRESH_TOKEN = "session.refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "session.access_expires_at"
        const val KEY_REFRESH_EXPIRES_AT = "session.refresh_expires_at"
        const val KEY_OFFLINE_ACCESS_UNTIL = "session.offline_access_until"
    }
}

@Singleton
class SecureLocalAuthGateStore @Inject constructor(
    private val storage: SecureAuthStorage,
    private val pinHasher: PinHasher,
) : LocalAuthGateStore {

    override suspend fun hasPin(): Boolean = withContext(Dispatchers.IO) {
        storage.read(KEY_PIN_HASH) != null &&
            storage.read(KEY_PIN_SALT) != null &&
            storage.read(KEY_PIN_ITERATIONS) != null
    }

    override suspend fun setPin(pin: CharArray) = withContext(Dispatchers.IO) {
        try {
            val verifier = pinHasher.create(pin)
            storage.write(
                mapOf(
                    KEY_PIN_SALT to verifier.saltBase64,
                    KEY_PIN_HASH to verifier.hashBase64,
                    KEY_PIN_ITERATIONS to verifier.iterations.toString(),
                )
            )
        } finally {
            pin.fill('\u0000')
        }
    }

    override suspend fun verifyPin(pin: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = storage.read(KEY_PIN_SALT) ?: return@withContext false
            val hash = storage.read(KEY_PIN_HASH) ?: return@withContext false
            val iterations = storage.read(KEY_PIN_ITERATIONS)?.toIntOrNull()
                ?: return@withContext false
            runCatching {
                pinHasher.verify(
                    pin,
                    PinVerifier(
                        saltBase64 = salt,
                        hashBase64 = hash,
                        iterations = iterations,
                    ),
                )
            }.getOrDefault(false)
        } finally {
            pin.fill('\u0000')
        }
    }

    override suspend fun clearPin() = withContext(Dispatchers.IO) {
        storage.write(
            mapOf(
                KEY_PIN_SALT to null,
                KEY_PIN_HASH to null,
                KEY_PIN_ITERATIONS to null,
            )
        )
    }

    override suspend fun isBiometricEnabled(): Boolean = withContext(Dispatchers.IO) {
        storage.read(KEY_BIOMETRIC_ENABLED)?.toBooleanStrictOrNull() ?: false
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        storage.write(mapOf(KEY_BIOMETRIC_ENABLED to enabled.toString()))
    }

    override suspend fun isLocked(): Boolean = withContext(Dispatchers.IO) {
        storage.read(KEY_LOCKED)?.toBooleanStrictOrNull() ?: false
    }

    override suspend fun setLocked(locked: Boolean) = withContext(Dispatchers.IO) {
        storage.write(mapOf(KEY_LOCKED to locked.toString()))
    }

    private companion object {
        const val KEY_PIN_SALT = "gate.pin_salt"
        const val KEY_PIN_HASH = "gate.pin_hash"
        const val KEY_PIN_ITERATIONS = "gate.pin_iterations"
        const val KEY_BIOMETRIC_ENABLED = "gate.biometric_enabled"
        const val KEY_LOCKED = "gate.locked"
    }
}
