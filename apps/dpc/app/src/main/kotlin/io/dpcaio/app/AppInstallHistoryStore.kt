package io.dpcaio.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.dpcaio.installer.InstallHistoryRecord
import io.dpcaio.installer.InstallHistoryStatus

class AppInstallHistoryStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun append(record: InstallHistoryRecord) {
        val next = buildList {
            add(record)
            addAll(recent(MAX_RECORDS).filterNot {
                it.timestampMillis == record.timestampMillis &&
                    it.packageName == record.packageName &&
                    it.status == record.status
            })
        }.take(MAX_RECORDS)
        prefs.edit().putString(KEY_RECORDS, next.joinToString("\n", transform = ::encode)).apply()
    }

    fun recent(limit: Int = 30): List<InstallHistoryRecord> = prefs.getString(KEY_RECORDS, "")
        .orEmpty()
        .lineSequence()
        .mapNotNull(::decode)
        .take(limit.coerceIn(1, MAX_RECORDS))
        .toList()

    private fun encode(record: InstallHistoryRecord): String = listOf(
        record.timestampMillis.toString(),
        record.status.name,
        Uri.encode(record.packageName),
        Uri.encode(record.detail.orEmpty()),
    ).joinToString("|")

    private fun decode(line: String): InstallHistoryRecord? {
        val parts = line.split('|', limit = 4)
        if (parts.size != 4) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val status = runCatching { InstallHistoryStatus.valueOf(parts[1]) }.getOrNull() ?: return null
        return InstallHistoryRecord(
            timestampMillis = timestamp,
            status = status,
            packageName = Uri.decode(parts[2]),
            detail = Uri.decode(parts[3]).ifBlank { null },
        )
    }

    companion object {
        private const val PREFS = "dpc-aio-app-install-history"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 100
    }
}
