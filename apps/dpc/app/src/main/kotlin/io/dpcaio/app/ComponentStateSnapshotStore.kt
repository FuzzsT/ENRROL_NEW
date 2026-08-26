package io.dpcaio.app

import android.content.Context
import io.dpcaio.activity.ComponentOverrideState

data class ComponentStateSnapshot(
    val userId: Int,
    val packageName: String,
    val className: String,
    val previousOverrideState: ComponentOverrideState,
    val timestampEpochMs: Long
)

class ComponentStateSnapshotStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("dpc_aio_component_snapshots", Context.MODE_PRIVATE)

    fun save(snapshot: ComponentStateSnapshot) {
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.removeAll { line -> line.split('|').let { it.size >= 4 && it[1] == snapshot.userId.toString() && it[2] == snapshot.packageName && it[3] == snapshot.className } }
        current += encode(snapshot)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun list(packageName: String, userId: Int): List<ComponentStateSnapshot> = prefs.getStringSet(KEY, emptySet()).orEmpty()
        .mapNotNull(::decode)
        .filter { it.packageName == packageName && it.userId == userId }
        .sortedByDescending { it.timestampEpochMs }

    fun clear(packageName: String, userId: Int) {
        val kept = prefs.getStringSet(KEY, emptySet()).orEmpty().filterNot { line ->
            decode(line)?.let { it.packageName == packageName && it.userId == userId } == true
        }.toSet()
        prefs.edit().putStringSet(KEY, kept).apply()
    }

    private fun encode(s: ComponentStateSnapshot) = listOf(
        s.timestampEpochMs.toString(), s.userId.toString(), s.packageName, s.className, s.previousOverrideState.name
    ).joinToString("|")

    private fun decode(line: String): ComponentStateSnapshot? {
        val p = line.split('|')
        if (p.size != 5) return null
        return runCatching {
            ComponentStateSnapshot(
                userId = p[1].toInt(),
                packageName = p[2],
                className = p[3],
                previousOverrideState = ComponentOverrideState.valueOf(p[4]),
                timestampEpochMs = p[0].toLong()
            )
        }.getOrNull()
    }

    companion object { private const val KEY = "snapshots" }
}
