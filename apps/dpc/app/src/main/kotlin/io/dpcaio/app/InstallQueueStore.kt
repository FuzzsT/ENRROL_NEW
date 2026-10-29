package io.dpcaio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class InstallQueueStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadQueue(): List<InstallQueueItem> = decodeQueue(prefs.getString(KEY_QUEUE, null))

    fun replaceQueue(items: List<InstallQueueItem>) {
        prefs.edit().putString(KEY_QUEUE, encodeQueue(items)).apply()
    }

    fun updateItem(item: InstallQueueItem): List<InstallQueueItem> {
        val next = loadQueue().map { if (it.id == item.id) item else it }
        replaceQueue(next)
        return next
    }

    fun loadHistory(): List<InstallHistoryEntry> = decodeHistory(prefs.getString(KEY_HISTORY, null))

    fun appendHistory(entry: InstallHistoryEntry) {
        val next = (listOf(entry) + loadHistory()).take(HISTORY_LIMIT)
        prefs.edit().putString(KEY_HISTORY, encodeHistory(next)).apply()
    }

    fun clearCompleted(): List<InstallQueueItem> {
        val completed = setOf(InstallQueueState.SUCCESS, InstallQueueState.FAILED, InstallQueueState.SKIPPED)
        val next = loadQueue().filterNot { it.state in completed }
        replaceQueue(next)
        return next
    }

    private fun encodeQueue(items: List<InstallQueueItem>): String = JSONArray().apply {
        items.forEach { put(itemToJson(it)) }
    }.toString()

    private fun decodeQueue(raw: String?): List<InstallQueueItem> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) add(itemFromJson(array.getJSONObject(i)))
        }
    }.getOrElse { emptyList() }

    private fun encodeHistory(items: List<InstallHistoryEntry>): String = JSONArray().apply {
        items.forEach { put(historyToJson(it)) }
    }.toString()

    private fun decodeHistory(raw: String?): List<InstallHistoryEntry> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) add(historyFromJson(array.getJSONObject(i)))
        }
    }.getOrElse { emptyList() }

    private fun itemToJson(item: InstallQueueItem) = JSONObject().apply {
        put("id", item.id)
        put("uri", item.uri)
        put("displayName", item.displayName)
        put("updaterMode", item.updaterMode)
        put("targetPackage", item.targetPackage)
        put("state", item.state.name)
        put("sessionId", item.sessionId)
        put("resolvedPackage", item.resolvedPackage)
        put("versionCode", item.versionCode)
        put("detail", item.detail)
        put("createdAt", item.createdAt)
        put("updatedAt", item.updatedAt)
    }

    private fun itemFromJson(json: JSONObject) = InstallQueueItem(
        id = json.getString("id"),
        uri = json.getString("uri"),
        displayName = json.optString("displayName"),
        updaterMode = json.optBoolean("updaterMode"),
        targetPackage = json.optNullableString("targetPackage"),
        state = runCatching { InstallQueueState.valueOf(json.optString("state")) }.getOrDefault(InstallQueueState.QUEUED),
        sessionId = if (json.isNull("sessionId")) null else json.optInt("sessionId"),
        resolvedPackage = json.optNullableString("resolvedPackage"),
        versionCode = if (json.isNull("versionCode")) null else json.optLong("versionCode"),
        detail = json.optNullableString("detail"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun historyToJson(entry: InstallHistoryEntry) = JSONObject().apply {
        put("itemId", entry.itemId)
        put("timestamp", entry.timestamp)
        put("packageName", entry.packageName)
        put("versionCode", entry.versionCode)
        put("displayName", entry.displayName)
        put("result", entry.result.name)
        put("detail", entry.detail)
    }

    private fun historyFromJson(json: JSONObject) = InstallHistoryEntry(
        itemId = json.getString("itemId"),
        timestamp = json.optLong("timestamp"),
        packageName = json.optNullableString("packageName"),
        versionCode = if (json.isNull("versionCode")) null else json.optLong("versionCode"),
        displayName = json.optString("displayName"),
        result = runCatching { InstallQueueState.valueOf(json.optString("result")) }.getOrDefault(InstallQueueState.FAILED),
        detail = json.optNullableString("detail"),
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS = "dpc-aio-install-queue"
        private const val KEY_QUEUE = "queue"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_LIMIT = 100
    }
}
