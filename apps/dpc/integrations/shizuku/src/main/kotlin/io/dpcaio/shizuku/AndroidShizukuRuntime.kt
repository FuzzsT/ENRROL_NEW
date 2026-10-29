package io.dpcaio.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShizukuRuntimeState(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val serverUid: Int?,
    val apiVersion: Int?,
    val identity: String
) {
    /** Diagnostic only: reports an already-present Shizuku service identity. */
    val isSystemUid: Boolean get() = serverUid == 1000
}

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
            1000 -> "system"
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

    fun observe(onChanged: (ShizukuRuntimeState) -> Unit): AutoCloseable {
        val binderReceived = Shizuku.OnBinderReceivedListener {
            onChanged(probe())
        }
        val binderDead = Shizuku.OnBinderDeadListener {
            onChanged(probe())
        }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
            onChanged(probe())
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        return AutoCloseable {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        }
    }
}
