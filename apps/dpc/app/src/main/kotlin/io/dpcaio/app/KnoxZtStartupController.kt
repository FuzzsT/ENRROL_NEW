package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import io.dpcaio.knoxzt.android.KnoxZtRecoveryManager
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.util.concurrent.Executors

object KnoxZtStartupController {
    const val ACTION_STATE_CHANGED = "io.dpcaio.action.KNOXZT_STATE_CHANGED"
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "dpc-aio-knoxzt") }

    fun enqueue(context: Context) {
        val app = context.applicationContext
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(app.packageName) && !dpm.isProfileOwnerApp(app.packageName)) return
        executor.execute {
            val result = KnoxZtRecoveryManager(app, AioDeviceAdminReceiver.componentName(app), shizuku = ShizukuUserServiceClient(app)).ensureReady()
            app.getSharedPreferences("knoxzt_runtime", Context.MODE_PRIVATE).edit()
                .putString("status", result.status.name)
                .putString("detail", result.detail)
                .putLong("updated_at", System.currentTimeMillis())
                .apply()
            app.sendBroadcast(android.content.Intent(ACTION_STATE_CHANGED).apply {
                setPackage(app.packageName)
                putExtra("status", result.status.name)
                putExtra("detail", result.detail)
            })
        }
    }
}
