package io.dpcaio.activity.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import io.dpcaio.activity.DiscoveredActivity

class AndroidActivityInventory(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    @Suppress("DEPRECATION")
    fun list(packageName: String, user: UserHandle): List<DiscoveredActivity> {
        val packageInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        )
        val launcherComponents = launcherApps.getActivityList(packageName, user)
            .map { it.componentName }
            .toSet()
        val userAccessible = launcherApps.profiles.contains(user)
        val packageUid = packageInfo.applicationInfo?.uid
        val sameUid = packageUid != null && packageUid == Process.myUid()

        return packageInfo.activities.orEmpty().map { info ->
            val component = ComponentName(info.packageName, info.name)
            val enabled = runCatching { launcherApps.isActivityEnabled(component, user) }
                .getOrDefault(info.enabled)
            DiscoveredActivity(
                packageName = info.packageName,
                className = info.name,
                enabled = enabled,
                exported = info.exported,
                launcherVisible = component in launcherComponents,
                requiredPermission = info.permission,
                sameUid = sameUid,
                userAccessible = userAccessible
            )
        }
    }
}
