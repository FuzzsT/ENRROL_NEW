package io.dpcaio.app

import android.content.Context

class EnterpriseLogStateStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("enterprise-log-state", Context.MODE_PRIVATE)

    fun markSecurityLogsAvailable() {
        prefs.edit()
            .putBoolean(KEY_SECURITY_AVAILABLE, true)
            .putLong(KEY_SECURITY_CALLBACK_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordNetworkBatchToken(batchToken: Long, networkLogsCount: Int) {
        prefs.edit()
            .putLong(KEY_NETWORK_TOKEN, batchToken)
            .putInt(KEY_NETWORK_COUNT, networkLogsCount)
            .putLong(KEY_NETWORK_CALLBACK_AT, System.currentTimeMillis())
            .apply()
    }

    fun securityLogsAvailable(): Boolean = prefs.getBoolean(KEY_SECURITY_AVAILABLE, false)
    fun consumeSecurityAvailability() { prefs.edit().putBoolean(KEY_SECURITY_AVAILABLE, false).apply() }
    fun networkBatchToken(): Long? = prefs.getLong(KEY_NETWORK_TOKEN, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    fun networkBatchCount(): Int = prefs.getInt(KEY_NETWORK_COUNT, 0)
    fun clearNetworkBatch() { prefs.edit().remove(KEY_NETWORK_TOKEN).remove(KEY_NETWORK_COUNT).apply() }

    companion object {
        private const val KEY_SECURITY_AVAILABLE = "security_available"
        private const val KEY_SECURITY_CALLBACK_AT = "security_callback_at"
        private const val KEY_NETWORK_TOKEN = "network_token"
        private const val KEY_NETWORK_COUNT = "network_count"
        private const val KEY_NETWORK_CALLBACK_AT = "network_callback_at"
    }
}
