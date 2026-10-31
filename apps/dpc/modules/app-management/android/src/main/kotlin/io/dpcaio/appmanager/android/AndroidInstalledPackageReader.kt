package io.dpcaio.appmanager.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import io.dpcaio.appmanager.InstalledPackageArtifacts
import java.io.File

class AndroidInstalledPackageReader(context: Context) {
    private val app = context.applicationContext
    private val packageManager: PackageManager = app.packageManager

    fun read(packageName: String): InstalledPackageArtifacts {
        val info = getPackageInfo(packageName)
        val applicationInfo = requireNotNull(info.applicationInfo) {
            "ApplicationInfo is unavailable for $packageName"
        }
        val baseApk = applicationInfo.sourceDir
        require(!baseApk.isNullOrBlank()) { "Base APK is unavailable for $packageName" }

        val splits = applicationInfo.splitSourceDirs
            ?.asSequence()
            ?.filterNotNull()
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.toList()
            .orEmpty()

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

        return InstalledPackageArtifacts(
            packageName = packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            baseApk = baseApk,
            splitApks = splits,
            obbFiles = findReadableObbFiles(packageName),
        )
    }

    private fun getPackageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

    private fun findReadableObbFiles(packageName: String): List<String> {
        @Suppress("DEPRECATION")
        val storageRoot = Environment.getExternalStorageDirectory()
        val obbDir = File(storageRoot, "Android/obb/$packageName")
        if (!obbDir.isDirectory || !obbDir.canRead()) return emptyList()
        return obbDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.canRead() && it.name.endsWith(".obb", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            ?.toList()
            .orEmpty()
    }
}
