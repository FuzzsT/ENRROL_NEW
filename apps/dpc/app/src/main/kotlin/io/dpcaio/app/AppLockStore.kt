package io.dpcaio.app

import android.content.Context

data class AppLockRecord(
    val packageName: String,
    val locked: Boolean,
    val unlockedUntilMillis: Long,
)

class AppLockStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(packageName: String): AppLockRecord? {
        if (packageName !in packageNames()) return null
        return AppLockRecord(
            packageName = packageName,
            locked = prefs.getBoolean(key(packageName, "locked"), false),
            unlockedUntilMillis = prefs.getLong(key(packageName, "unlockedUntilMillis"), 0L),
        )
    }

    fun write(record: AppLockRecord): Boolean {
        val packages = packageNames().toMutableSet().apply { add(record.packageName) }
        return prefs.edit()
            .putStringSet(KEY_PACKAGES, packages)
            .putBoolean(key(record.packageName, "locked"), record.locked)
            .putLong(key(record.packageName, "unlockedUntilMillis"), record.unlockedUntilMillis)
            .commit()
    }

    fun remove(packageName: String): Boolean {
        val packages = packageNames().toMutableSet().apply { remove(packageName) }
        return prefs.edit()
            .putStringSet(KEY_PACKAGES, packages)
            .remove(key(packageName, "locked"))
            .remove(key(packageName, "unlockedUntilMillis"))
            .commit()
    }

    fun all(): List<AppLockRecord> = packageNames().mapNotNull(::read)

    private fun packageNames(): Set<String> = prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    private fun key(packageName: String, field: String): String = "$packageName::$field"

    companion object {
        private const val PREFS_NAME = "app_lock_state"
        private const val KEY_PACKAGES = "packages"
    }
}
