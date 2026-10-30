package io.dpcaio.installer.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class InstallSourceDetails(
    val installingPackageName: String?,
    val installingPackageLabel: String?,
    val initiatingPackageName: String?,
    val originatingPackageName: String?,
    val packageSource: Int?,
    val updateOwnerPackageName: String?,
)

class AndroidInstallSourceReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun read(packageName: String): InstallSourceDetails {
        if (Build.VERSION.SDK_INT < 30) {
            val installing = runCatching { packageManager.getInstallerPackageName(packageName) }.getOrNull()
            return InstallSourceDetails(
                installingPackageName = installing,
                installingPackageLabel = installing?.let(::labelFor),
                initiatingPackageName = null,
                originatingPackageName = null,
                packageSource = null,
                updateOwnerPackageName = null,
            )
        }

        val info = runCatching { packageManager.getInstallSourceInfo(packageName) }.getOrNull()
        val installing = info?.installingPackageName
        return InstallSourceDetails(
            installingPackageName = installing,
            installingPackageLabel = installing?.let(::labelFor),
            initiatingPackageName = info?.initiatingPackageName,
            originatingPackageName = info?.originatingPackageName,
            packageSource = if (Build.VERSION.SDK_INT >= 33) info?.packageSource else null,
            updateOwnerPackageName = if (Build.VERSION.SDK_INT >= 34) info?.updateOwnerPackageName else null,
        )
    }

    private fun labelFor(packageName: String): String? = runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrNull()
}
