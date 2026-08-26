package io.dpcaio.activity.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.ComponentStateResolver
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
            val manifestEnabled = info.enabled
            val overrideState = when (runCatching { packageManager.getComponentEnabledSetting(component) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> ComponentOverrideState.ENABLED
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> ComponentOverrideState.DISABLED
                else -> ComponentOverrideState.DEFAULT
            }
            val effectiveEnabled = runCatching { launcherApps.isActivityEnabled(component, user) }
                .getOrElse { ComponentStateResolver.effective(manifestEnabled, overrideState) }
            DiscoveredActivity(
                packageName = info.packageName,
                className = info.name,
                enabled = effectiveEnabled,
                exported = info.exported,
                launcherVisible = component in launcherComponents,
                requiredPermission = info.permission,
                sameUid = sameUid,
                userAccessible = userAccessible,
                manifestEnabled = manifestEnabled,
                overrideState = overrideState,
                effectiveEnabled = effectiveEnabled
            )
        }
    }
}
