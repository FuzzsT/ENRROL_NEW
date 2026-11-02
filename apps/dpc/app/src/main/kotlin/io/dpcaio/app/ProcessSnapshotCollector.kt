package io.dpcaio.app

import android.app.ActivityManager
import android.content.Context
import io.dpcaio.appmanager.PackageExportPlan
import org.json.JSONArray
import org.json.JSONObject

data class ProcessSnapshot(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val importance: Int,
    val packages: List<String>,
) {
    val primaryPackage: String?
        get() = packages.firstOrNull { processName == it || processName.startsWith("$it:") }
            ?: packages.firstOrNull()

    override fun toString(): String = buildString {
        append(processName)
        append(" • pid=")
        append(pid)
        append(" uid=")
        append(uid)
        append(" • ")
        append(importanceLabel(importance))
        if (packages.isNotEmpty()) {
            append(" • ")
            append(packages.joinToString())
        }
    }

    private fun importanceLabel(value: Int): String = when (value) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        else -> "importance=$value"
    }
}

class ProcessSnapshotCollector(context: Context) {
    private val app = context.applicationContext
    private val activityManager: ActivityManager = requireNotNull(app.getSystemService(ActivityManager::class.java))

    fun collect(): List<ProcessSnapshot> = activityManager.runningAppProcesses
        .orEmpty()
        .asSequence()
        .map { info ->
            ProcessSnapshot(
                pid = info.pid,
                uid = info.uid,
                processName = info.processName.orEmpty(),
                importance = info.importance,
                packages = info.pkgList
                    ?.asSequence()
                    ?.filterNotNull()
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.sorted()
                    ?.toList()
                    .orEmpty(),
            )
        }
        .sortedWith(compareBy<ProcessSnapshot>({ it.importance }, { it.processName }, { it.pid }))
        .toList()

    fun snapshotEntryForPackage(packageName: String): Pair<String, String> =
        PackageExportPlan.PROCESS_SNAPSHOT_ENTRY to jsonForPackage(packageName)

    fun jsonForPackage(packageName: String): String {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        val matching = collect().filter { snapshot ->
            packageName in snapshot.packages ||
                snapshot.processName == packageName ||
                snapshot.processName.startsWith("$packageName:")
        }
        val processes = JSONArray()
        matching.forEach { snapshot ->
            processes.put(
                JSONObject()
                    .put("pid", snapshot.pid)
                    .put("uid", snapshot.uid)
                    .put("processName", snapshot.processName)
                    .put("importance", snapshot.importance)
                    .put("packages", JSONArray(snapshot.packages))
            )
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("entryName", "process-snapshot.json")
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("packageName", packageName)
            .put("privateDataIncluded", false)
            .put("privateDataReason", "Android app-private data is not copied; only PackageManager artifacts, readable OBB and process metadata are included")
            .put("visibilityBoundary", "runningAppProcesses is best-effort and may be restricted by the Android version and device policy")
            .put("processes", processes)
            .toString(2)
    }
}
