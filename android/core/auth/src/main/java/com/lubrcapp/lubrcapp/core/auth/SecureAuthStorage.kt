package com.lubrcapp.lubrcapp.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecureAuthStorage {
    fun read(key: String): String?
    fun write(values: Map<String, String?>)
    fun clear()
}

@Singleton
class AndroidKeystoreSecureAuthStorage @Inject constructor(
    @ApplicationContext context: Context,
) : SecureAuthStorage {

    private val preferences = context.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )

    override fun read(key: String): String? = synchronized(lock) {
        val encrypted = preferences.getString(key, null) ?: return@synchronized null
        decrypt(encrypted)
    }

    override fun write(values: Map<String, String?>) = synchronized(lock) {
        val encryptedValues = values.mapValues { (_, value) ->
            value?.let(::encrypt)
        }

        val editor = preferences.edit()
        encryptedValues.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        check(editor.commit()) { "Failed to persist secure authentication state" }
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().clear().commit()) {
            "Failed to clear secure authentication state"
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + cipherText.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(cipherText)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? =
        try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(payload)
            val ivLength = buffer.int
            if (ivLength !in 12..32 || buffer.remaining() <= ivLength) return null

            val iv = ByteArray(ivLength)
            buffer.get(iv)
            val cipherText = ByteArray(buffer.remaining())
            buffer.get(cipherText)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val lock = Any()
        const val PREFERENCES_FILE = "secure_auth_state"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "businessos.auth.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
