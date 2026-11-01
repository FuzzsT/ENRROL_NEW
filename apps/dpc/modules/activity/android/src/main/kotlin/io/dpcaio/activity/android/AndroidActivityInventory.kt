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
        val currentUser = UserHandle.getUserHandleForUid(Process.myUid())
        if (!targetPackageAvailable(packageName, user, currentUser)) return emptyList()
        val packageInfo = try {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
        } catch (_: PackageManager.NameNotFoundException) {
            return launcherFallback(packageName, user)
        }
        val launcherComponents = launcherApps.getActivityList(packageName, user)
            .map { it.componentName }
            .toSet()
        val userAccessible = launcherApps.profiles.contains(user)
        val packageUid = packageInfo.applicationInfo?.uid
        val sameUid = user == currentUser && packageUid != null && packageUid == Process.myUid()

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

    private fun targetPackageAvailable(packageName: String, user: UserHandle, currentUser: UserHandle): Boolean {
        if (user == currentUser) return true
        if (user !in launcherApps.profiles) return false
        return runCatching {
            launcherApps.getApplicationInfo(packageName, 0, user)
        }.getOrNull() != null
    }

    private fun launcherFallback(packageName: String, user: UserHandle): List<DiscoveredActivity> {
        val currentUser = UserHandle.getUserHandleForUid(Process.myUid())
        val userAccessible = launcherApps.profiles.contains(user)
        return launcherApps.getActivityList(packageName, user).map { launcherInfo ->
            val component = launcherInfo.componentName
            val effectiveEnabled = runCatching { launcherApps.isActivityEnabled(component, user) }.getOrDefault(true)
            DiscoveredActivity(
                packageName = component.packageName,
                className = component.className,
                enabled = effectiveEnabled,
                exported = true,
                launcherVisible = true,
                requiredPermission = null,
                sameUid = user == currentUser && launcherInfo.applicationInfo.uid == Process.myUid(),
                userAccessible = userAccessible,
                manifestEnabled = true,
                overrideState = ComponentOverrideState.DEFAULT,
                effectiveEnabled = effectiveEnabled
            )
        }
    }
}
