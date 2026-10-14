package com.lubrcapp.lubrcapp.core.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed credential persistence. No plaintext token is written to disk.
 * Clearing pairing disables authenticated sync without affecting local business records.
 */
class SecureDeviceCredentials(context: Context) : DeviceTokenProvider {
    private val preferences = context.applicationContext.getSharedPreferences("lubric_secure_pairing", Context.MODE_PRIVATE)
    private val alias = "lubric_vps_device_token_v1"

    override suspend fun token(): String? {
        val iv = preferences.getString("iv", null) ?: return null
        val encrypted = preferences.getString("token", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Keystore key invalidated, corrupt storage or device restore: require re-pairing.
            null
        }
    }

    fun saveToken(token: String) {
        require(token.isNotBlank()) { "Pairing token cannot be blank" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()) { "Unable to persist paired device credentials" }
    }

    fun clearPairing() {
        preferences.edit().remove("iv").remove("token").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
