package io.dpcaio.permission.android

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager

data class SpecialAccessSnapshot(
    val packageName: String,
    val usageAccess: Boolean,
    val requestInstallPackages: Boolean?,
    val notificationListener: Boolean
)

class SpecialAccessInspector(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)

    fun inspect(packageName: String): SpecialAccessSnapshot {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val usage = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, packageName) == AppOpsManager.MODE_ALLOWED
        val requestInstall = if (packageName == appContext.packageName) pm.canRequestPackageInstalls() else null
        val listeners = NotificationManager.getEnabledListenerPackages(appContext)
        return SpecialAccessSnapshot(packageName, usage, requestInstall, packageName in listeners)
    }
}
