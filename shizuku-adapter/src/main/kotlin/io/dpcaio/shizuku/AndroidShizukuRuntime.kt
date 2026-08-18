package io.dpcaio.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShizukuRuntimeState(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val serverUid: Int?,
    val apiVersion: Int?,
    val identity: String
)

class AndroidShizukuRuntime {
    fun probe(): ShizukuRuntimeState {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return ShizukuRuntimeState(false, false, null, null, "unavailable")
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = runCatching { Shizuku.getUid() }.getOrNull()
        val version = runCatching { Shizuku.getVersion() }.getOrNull()
        val identity = when (uid) {
            0 -> "root"
            2000 -> "shell"
            null -> "unknown"
            else -> "uid:$uid"
        }
        return ShizukuRuntimeState(true, granted, uid, version, identity)
    }

    fun requestPermission(requestCode: Int): Boolean {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return false
        Shizuku.requestPermission(requestCode)
        return true
    }
}
