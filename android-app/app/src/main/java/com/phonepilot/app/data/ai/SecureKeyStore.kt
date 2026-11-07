package com.phonepilot.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys encrypted with an Android Keystore key (AES/GCM).
 *
 * Only the ciphertext lives in SharedPreferences; the key never leaves the
 * Keystore. No third-party crypto dependency is added. If the Keystore entry is
 * lost (for example after a backup restore), reads return null and the user is
 * asked to enter the key again.
 */
object SecureKeyStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "phonepilot_ai_provider_keys"
    private const val PREFS = "phonepilot_secure_keys"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    @Synchronized
    fun put(context: Context, id: String, secret: String) {
        if (secret.isBlank()) {
            remove(context, id)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        prefs(context).edit()
            .putString(id, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    fun get(context: Context, id: String): String? {
        val stored = prefs(context).getString(id, null) ?: return null
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= 12) return null
            val iv = combined.copyOfRange(0, 12)
            val payload = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            String(cipher.doFinal(payload), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            // Key invalidated or data corrupt: drop it instead of crashing.
            remove(context, id)
            null
        }
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().remove(id).apply()
    }

    fun has(context: Context, id: String): Boolean = get(context, id)?.isNotBlank() == true

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
