package io.dpcaio.appmanager.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import io.dpcaio.appmanager.ManagedAppRecord
import io.dpcaio.policy.DevicePolicyGateway

class AndroidAppInventory(
    context: Context,
    private val policyGateway: DevicePolicyGateway
) {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun listInstalledApps(): List<ManagedAppRecord> {
        return packageManager.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                val hidden = policyGateway.isApplicationHidden(info.packageName).takeIf { it.isSuccess }?.value
                val suspended = policyGateway.isPackageSuspended(info.packageName).takeIf { it.isSuccess }?.value
                ManagedAppRecord(
                    packageName = info.packageName,
                    label = app.loadLabel(packageManager).toString(),
                    versionName = info.versionName,
                    uid = app.uid,
                    userId = UserHandle.getUserId(app.uid),
                    systemApp = app.flags and systemFlags != 0,
                    enabled = app.enabled,
                    hidden = hidden,
                    suspended = suspended
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
