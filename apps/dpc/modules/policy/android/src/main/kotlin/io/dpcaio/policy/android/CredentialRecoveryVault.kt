package io.dpcaio.policy.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CredentialRecoveryVault(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(scope: String, token: ByteArray) {
        require(token.size >= 32) { "Reset token must contain at least 32 bytes" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token)
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(key(scope), encoded).commit()
        ciphertext.fill(0)
    }

    fun get(scope: String): ByteArray? {
        val encoded = prefs.getString(key(scope), null) ?: return null
        val parts = encoded.split('.', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                cipher.doFinal(ciphertext)
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
        }.getOrNull()
    }

    fun contains(scope: String): Boolean = prefs.contains(key(scope))

    fun remove(scope: String) {
        prefs.edit().remove(key(scope)).commit()
    }

    private fun key(scope: String): String = MessageDigest.getInstance("SHA-256")
        .digest(scope.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUnlockedDeviceRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "dpc_credential_recovery_v2"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dpc-aio-credential-recovery-v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
