package io.dpcaio.appmanager.android

import android.content.Context

data class PinGuardRecord(
    val algorithm: String,
    val salt: String,
    val iterations: Int,
    val verifier: String,
    val failedAttempts: Int,
    val lockoutUntilMillis: Long,
)

class AndroidPinGuardStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): PinGuardRecord? {
        val algorithm = preferences.getString(KEY_ALGORITHM, null) ?: return null
        val salt = preferences.getString(KEY_SALT, null) ?: return null
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: return null
        val iterations = preferences.getInt(KEY_ITERATIONS, 0)
        if (iterations <= 0) return null
        return PinGuardRecord(
            algorithm = algorithm,
            salt = salt,
            iterations = iterations,
            verifier = verifier,
            failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0),
            lockoutUntilMillis = preferences.getLong(KEY_LOCKOUT_UNTIL, 0L).coerceAtLeast(0L),
        )
    }

    fun write(record: PinGuardRecord): Boolean = preferences.edit()
        .putString(KEY_ALGORITHM, record.algorithm)
        .putString(KEY_SALT, record.salt)
        .putInt(KEY_ITERATIONS, record.iterations)
        .putString(KEY_VERIFIER, record.verifier)
        .putInt(KEY_FAILED_ATTEMPTS, record.failedAttempts)
        .putLong(KEY_LOCKOUT_UNTIL, record.lockoutUntilMillis)
        .commit()

    fun clear(): Boolean = preferences.edit().clear().commit()

    companion object {
        private const val PREFS_NAME = "pin_guard_v1"
        private const val KEY_ALGORITHM = "algorithm"
        private const val KEY_SALT = "salt"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_FAILED_ATTEMPTS = "failedAttempts"
        private const val KEY_LOCKOUT_UNTIL = "lockoutUntilMillis"
    }
}
