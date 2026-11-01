package io.dpcaio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class KnoxLicenseAttemptSource {
    PROVISIONING,
    RETRY_JOB,
    MANUAL,
    BROADCAST,
}

data class KnoxLicenseHistoryEntry(
    val timestampEpochMs: Long,
    val source: KnoxLicenseAttemptSource,
    val previousStatus: KnoxLicenseActivationStatus?,
    val newStatus: KnoxLicenseActivationStatus,
    val detail: String,
    val errorCode: Int?,
)

class KnoxLicenseHistoryStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun append(
        source: KnoxLicenseAttemptSource,
        previous: KnoxLicenseActivationSnapshot?,
        current: KnoxLicenseActivationSnapshot,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ): KnoxLicenseHistoryEntry {
        val entry = KnoxLicenseHistoryEntry(
            timestampEpochMs = timestampEpochMs,
            source = source,
            previousStatus = previous?.status,
            newStatus = current.status,
            detail = current.detail,
            errorCode = current.errorCode,
        )
        val items = readAll().toMutableList()
        items += entry
        val bounded = items.takeLast(MAX_ENTRIES)
        val array = JSONArray()
        bounded.forEach { item ->
            array.put(
                JSONObject()
                    .put("timestampEpochMs", item.timestampEpochMs)
                    .put("source", item.source.name)
                    .put("previousStatus", item.previousStatus?.name ?: JSONObject.NULL)
                    .put("newStatus", item.newStatus.name)
                    .put("detail", item.detail)
                    .put("errorCode", item.errorCode ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        return entry
    }

    fun readRecent(limit: Int = 10): List<KnoxLicenseHistoryEntry> =
        readAll().takeLast(limit.coerceIn(0, MAX_ENTRIES)).asReversed()

    private fun readAll(): List<KnoxLicenseHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val source = KnoxLicenseAttemptSource.valueOf(item.getString("source"))
                    val previous = item.optString("previousStatus")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.let { KnoxLicenseActivationStatus.valueOf(it) }
                    val next = KnoxLicenseActivationStatus.valueOf(item.getString("newStatus"))
                    add(
                        KnoxLicenseHistoryEntry(
                            timestampEpochMs = item.getLong("timestampEpochMs"),
                            source = source,
                            previousStatus = previous,
                            newStatus = next,
                            detail = item.optString("detail", "UNKNOWN"),
                            errorCode = if (item.isNull("errorCode")) null else item.getInt("errorCode"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS = "dpc_knox_license_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 50
    }
}
