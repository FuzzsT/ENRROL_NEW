package io.dpcaio.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EnrollmentSecrets(val enrollmentToken: String? = null, val password: String? = null)

class EnrollmentSecretStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(secretRef: String, secrets: EnrollmentSecrets) {
        val plaintext = JSONObject().apply {
            put("token", secrets.enrollmentToken ?: JSONObject.NULL)
            put("password", secrets.password ?: JSONObject.NULL)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        plaintext.fill(0)
        val encoded = Base64.getEncoder().encodeToString(cipher.iv) + "." + Base64.getEncoder().encodeToString(ciphertext)
        prefs.edit().putString(secretRef, encoded).commit()
    }

    fun get(secretRef: String?): EnrollmentSecrets? {
        if (secretRef.isNullOrBlank()) return null
        val encoded = prefs.getString(secretRef, null) ?: return null
        val parts = encoded.split('.', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.getDecoder().decode(parts[0])
            val ciphertext = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            try {
                val json = JSONObject(String(plaintext, Charsets.UTF_8))
                EnrollmentSecrets(
                    enrollmentToken = json.optString("token").takeIf { it.isNotBlank() && it != "null" },
                    password = json.optString("password").takeIf { it.isNotBlank() && it != "null" },
                )
            } finally {
                plaintext.fill(0)
            }
        }.getOrNull()
    }

    fun remove(secretRef: String?) {
        if (!secretRef.isNullOrBlank()) prefs.edit().remove(secretRef).commit()
    }

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
        private const val PREFS = "dpc_enrollment_secrets"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dpc-aio-enrollment-secrets-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
