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

enum class KnoxLicenseHistoryFilter {
    ALL,
    FAILED,
    RETRYABLE,
    MANUAL,
}

data class KnoxLicenseHistoryEntry(
    val timestampEpochMs: Long,
    val source: KnoxLicenseAttemptSource,
    val previousStatus: KnoxLicenseActivationStatus?,
    val newStatus: KnoxLicenseActivationStatus,
    val detail: String,
    val errorCode: Int?,
)

data class KnoxLicenseHistorySummary(
    val totalAttempts: Int,
    val lastSuccessEpochMs: Long?,
    val lastFailureEpochMs: Long?,
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
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        return entry
    }

    fun readRecent(limit: Int = 10, filter: KnoxLicenseHistoryFilter = KnoxLicenseHistoryFilter.ALL): List<KnoxLicenseHistoryEntry> {
        val filtered = readAll().filter { entry ->
            when (filter) {
                KnoxLicenseHistoryFilter.ALL -> true
                KnoxLicenseHistoryFilter.FAILED -> entry.newStatus == KnoxLicenseActivationStatus.FAILED
                KnoxLicenseHistoryFilter.RETRYABLE -> entry.newStatus == KnoxLicenseActivationStatus.RETRYABLE
                KnoxLicenseHistoryFilter.MANUAL -> entry.source == KnoxLicenseAttemptSource.MANUAL
            }
        }
        return filtered.takeLast(limit.coerceIn(0, MAX_ENTRIES)).asReversed()
    }

    fun summary(): KnoxLicenseHistorySummary {
        val items = readAll()
        return KnoxLicenseHistorySummary(
            totalAttempts = items.size,
            lastSuccessEpochMs = items.lastOrNull { it.newStatus == KnoxLicenseActivationStatus.ACTIVE }?.timestampEpochMs,
            lastFailureEpochMs = items.lastOrNull { it.newStatus == KnoxLicenseActivationStatus.FAILED }?.timestampEpochMs,
        )
    }

    fun exportJson(): String {
        val items = readAll()
        val summary = summary()
        val entries = JSONArray()
        items.forEach { entries.put(it.toJson()) }
        return JSONObject()
            .put("schema", "dpc-aio.knox-kpe-history.v1")
            .put(
                "summary",
                JSONObject()
                    .put("totalAttempts", summary.totalAttempts)
                    .put("lastSuccessEpochMs", summary.lastSuccessEpochMs ?: JSONObject.NULL)
                    .put("lastFailureEpochMs", summary.lastFailureEpochMs ?: JSONObject.NULL)
            )
            .put("entries", entries)
            .toString(2)
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun KnoxLicenseHistoryEntry.toJson(): JSONObject =
        JSONObject()
            .put("timestampEpochMs", timestampEpochMs)
            .put("source", source.name)
            .put("previousStatus", previousStatus?.name ?: JSONObject.NULL)
            .put("newStatus", newStatus.name)
            .put("detail", detail)
            .put("errorCode", errorCode ?: JSONObject.NULL)

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
