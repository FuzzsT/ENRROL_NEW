package io.dpcaio.knoxzt.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import io.dpcaio.knoxzt.KnoxZtProbe

class KnoxZtPackageInspector(context: Context) {
    private val pm = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun inspect(): KnoxZtProbe {
        val installed = runCatching {
            pm.getApplicationInfo(KNOXZT_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrNull()
        val known = installed ?: runCatching {
            pm.getApplicationInfo(
                KNOXZT_PACKAGE,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        }.getOrNull()
        val flags = known?.flags ?: 0
        val systemApp = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return KnoxZtProbe(
            installedForUser = installed != null,
            knownToSystem = known != null,
            enabled = installed?.enabled == true,
            systemApp = systemApp,
            trusted = systemApp
        )
    }
}
