package io.dpcaio.samsung.firmware.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.samsung.firmware.SamsungFirmwareEvidenceCatalog
import io.dpcaio.samsung.firmware.SamsungFirmwarePackageProbe
import io.dpcaio.samsung.firmware.SamsungFirmwareProfile

class AndroidSamsungFirmwareProbe(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val properties = AndroidSystemPropertyReader()

    fun read(): SamsungFirmwareProfile {
        val omcPath = firstProperty(SamsungFirmwareEvidenceCatalog.omcPathPropertyKeys.take(1))
        val omcEtcPath = firstProperty(SamsungFirmwareEvidenceCatalog.omcPathPropertyKeys.drop(1))
        return SamsungFirmwareProfile(
            samsungDevice = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
            salesCode = firstProperty(SamsungFirmwareEvidenceCatalog.salesCodePropertyKeys),
            multiCsc = properties.get(SamsungFirmwareEvidenceCatalog.multiCscProperty),
            countryIso = firstProperty(SamsungFirmwareEvidenceCatalog.countryIsoPropertyKeys),
            omcPath = omcPath,
            omcEtcPath = omcEtcPath,
            omcBuildVersion = properties.get(SamsungFirmwareEvidenceCatalog.omcBuildVersionProperty),
            buildPda = properties.get(SamsungFirmwareEvidenceCatalog.buildPdaProperty),
            buildIncremental = properties.get(SamsungFirmwareEvidenceCatalog.buildIncrementalProperty)
                ?: Build.VERSION.INCREMENTAL.takeIf { it.isNotBlank() },
            propertyAccessAvailable = properties.available,
            packages = SamsungFirmwareEvidenceCatalog.packageRoles.map { (packageName, role) ->
                packageProbe(packageName, role)
            },
        )
    }

    private fun firstProperty(keys: List<String>): String? =
        keys.asSequence().mapNotNull(properties::get).firstOrNull()

    private fun packageProbe(packageName: String, role: String): SamsungFirmwarePackageProbe {
        val info = applicationInfo(packageName)
        return SamsungFirmwarePackageProbe(
            packageName = packageName,
            role = role,
            installed = info != null,
            enabled = info?.enabled,
            systemApp = info?.let {
                (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            },
        )
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    }.getOrNull()
}

private class AndroidSystemPropertyReader {
    private val systemPropertiesClass = runCatching {
        Class.forName("android.os.SystemProperties")
    }.getOrNull()

    private val getMethod = runCatching {
        systemPropertiesClass?.getMethod("get", String::class.java, String::class.java)
    }.getOrNull()

    val available: Boolean
        get() = getMethod != null

    fun get(key: String): String? {
        val method = getMethod ?: return null
        val value = runCatching {
            method.invoke(null, key, "") as? String
        }.getOrNull()?.trim().orEmpty()
        return value.takeIf { it.isNotEmpty() }
    }
}
