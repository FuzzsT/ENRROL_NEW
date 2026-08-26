package io.dpcaio.app

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local access PIN for the human-facing DPC UI.
 *
 * This intentionally does not gate Android provisioning entry points such as
 * ProvisioningModeActivity or PolicyComplianceActivity. ManagedProvisioning must be
 * able to call those components without an interactive app PIN prompt.
 */
object DpcPinManager {
    private const val PREFS = "dpc_app_pin"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_BLOCKED_UNTIL = "blocked_until_epoch_ms"

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 12
    private const val MAX_FAILURES_BEFORE_DELAY = 5
    private const val FAILURE_DELAY_MS = 30_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString(KEY_SALT, null).isNullOrBlank() && !p.getString(KEY_HASH, null).isNullOrBlank()
    }

    fun isEnabled(context: Context): Boolean = isConfigured(context) && prefs(context).getBoolean(KEY_ENABLED, false)

    fun validateFormat(pin: String): String? = when {
        pin.length < MIN_PIN_LENGTH -> "PIN must contain at least $MIN_PIN_LENGTH digits"
        pin.length > MAX_PIN_LENGTH -> "PIN may contain at most $MAX_PIN_LENGTH digits"
        pin.any { !it.isDigit() } -> "PIN must contain digits only"
        else -> null
    }

    fun setPin(context: Context, pin: String, enabled: Boolean = true) {
        require(validateFormat(pin) == null) { validateFormat(pin) ?: "Invalid PIN" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_BLOCKED_UNTIL)
            .apply()
        if (enabled) DpcPinSession.markUnlocked() else DpcPinSession.lock()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        require(isConfigured(context)) { "PIN is not configured" }
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            clearFailures(context)
            DpcPinSession.lock()
        }
    }

    fun clearPin(context: Context) {
        prefs(context).edit().clear().apply()
        DpcPinSession.lock()
    }

    fun blockedRemainingMs(context: Context, nowEpochMs: Long = System.currentTimeMillis()): Long {
        val until = prefs(context).getLong(KEY_BLOCKED_UNTIL, 0L)
        return (until - nowEpochMs).coerceAtLeast(0L)
    }

    fun verify(context: Context, pin: String): Boolean {
        if (blockedRemainingMs(context) > 0L) return false
        val p = prefs(context)
        val saltText = p.getString(KEY_SALT, null) ?: return false
        val hashText = p.getString(KEY_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltText, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashText, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = derive(pin, salt)
        val ok = MessageDigest.isEqual(expected, actual)
        if (ok) {
            clearFailures(context)
        } else {
            val failures = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = p.edit().putInt(KEY_FAILED_ATTEMPTS, failures)
            if (failures >= MAX_FAILURES_BEFORE_DELAY) {
                editor.putLong(KEY_BLOCKED_UNTIL, System.currentTimeMillis() + FAILURE_DELAY_MS)
                editor.putInt(KEY_FAILED_ATTEMPTS, 0)
            }
            editor.apply()
        }
        return ok
    }

    private fun clearFailures(context: Context) {
        prefs(context).edit().putInt(KEY_FAILED_ATTEMPTS, 0).remove(KEY_BLOCKED_UNTIL).apply()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

object DpcPinSession {
    private const val SESSION_TIMEOUT_MS = 5 * 60_000L
    @Volatile private var unlockedAtElapsedMs: Long = 0L

    fun markUnlocked() {
        unlockedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    fun isUnlocked(): Boolean {
        val at = unlockedAtElapsedMs
        return at > 0L && SystemClock.elapsedRealtime() - at <= SESSION_TIMEOUT_MS
    }

    fun lock() {
        unlockedAtElapsedMs = 0L
    }
}
