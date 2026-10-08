package com.lubrcapp.lubrcapp.core.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

data class PinVerifier(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

class PinHasher @Inject constructor() {
    fun create(pin: CharArray): PinVerifier {
        validate(pin)
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt, ITERATIONS)
        return PinVerifier(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            iterations = ITERATIONS,
        )
    }

    fun verify(pin: CharArray, verifier: PinVerifier): Boolean {
        validate(pin)
        val salt = Base64.decode(verifier.saltBase64, Base64.NO_WRAP)
        val expected = Base64.decode(verifier.hashBase64, Base64.NO_WRAP)
        val actual = derive(pin, salt, verifier.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun validate(pin: CharArray) {
        require(pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            "PIN must be $MIN_PIN_LENGTH-$MAX_PIN_LENGTH digits"
        }
        require(pin.all(Char::isDigit)) { "PIN must contain digits only" }
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 12
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ITERATIONS = 120_000
    }
}
