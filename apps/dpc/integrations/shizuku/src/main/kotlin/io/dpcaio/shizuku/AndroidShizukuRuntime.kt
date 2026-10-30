package io.dpcaio.shizuku

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import rikka.sui.Sui

data class ShizukuRuntimeState(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val serverUid: Int?,
    val apiVersion: Int?,
    val identity: String,
    val backend: String,
) {
    /** Diagnostic only: reports an already-present Shizuku service identity. */
    val isSystemUid: Boolean get() = serverUid == 1000
}

class AndroidShizukuRuntime {
    fun probe(): ShizukuRuntimeState {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val sui = runCatching { Sui.isSui() }.getOrDefault(false)
        val backend = when {
            sui -> "sui"
            alive -> "shizuku"
            else -> "none"
        }
        if (!alive) {
            return ShizukuRuntimeState(false, false, null, null, "unavailable", backend)
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
        return ShizukuRuntimeState(true, granted, uid, version, identity, backend)
    }

    fun requestPermission(requestCode: Int): Boolean {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return false
        Shizuku.requestPermission(requestCode)
        return true
    }

    fun observe(onChanged: (ShizukuRuntimeState) -> Unit): AutoCloseable {
        val handler = Handler(Looper.getMainLooper())
        val retryRunnables = mutableListOf<Runnable>()
        val publish = { onChanged(probe()) }

        val binderReceived = Shizuku.OnBinderReceivedListener { publish() }
        val binderDead = Shizuku.OnBinderDeadListener { publish() }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> publish() }

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        onChanged(probe())
        RETRY_DELAYS_MS.forEach { delay ->
            val retry = Runnable { onChanged(probe()) }
            retryRunnables += retry
            handler.postDelayed(retry, delay)
        }

        return AutoCloseable {
            retryRunnables.forEach { handler.removeCallbacks(it) }
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        }
    }

    companion object {
        private val RETRY_DELAYS_MS = longArrayOf(250L, 1000L, 2500L)
    }
}
