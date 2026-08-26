package io.dpcaio.samsung.settings.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.dpcaio.samsung.settings.SettingGateway
import io.dpcaio.samsung.settings.SettingNamespace
import io.dpcaio.samsung.settings.SettingWriteRoute
import io.dpcaio.shizuku.ShizukuUserServiceClient

class AndroidSamsungSettingGateway(
    context: Context,
    private val shizuku: ShizukuUserServiceClient? = null,
    private val userId: Int = 0,
    private val knoxDeepSettings: KnoxDeepSettingsGateway? = null
) : SettingGateway {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun read(namespace: SettingNamespace, key: String): String? = when (namespace) {
        SettingNamespace.SYSTEM -> Settings.System.getString(resolver, key)
        SettingNamespace.SECURE -> Settings.Secure.getString(resolver, key)
        SettingNamespace.GLOBAL -> Settings.Global.getString(resolver, key)
        SettingNamespace.KNOX_DEEP_SETTING, SettingNamespace.SAMSUNG_CUSTOM -> knoxDeepSettings?.takeIf { it.isAvailable() && it.supports(key) }?.read(key)
    }

    override fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean = when (route) {
        SettingWriteRoute.PUBLIC_SETTINGS -> writePublic(namespace, key, value)
        SettingWriteRoute.SHIZUKU_SETTINGS -> shizuku?.writeSetting(namespace.name.lowercase(), key, value, userId) == 0
        SettingWriteRoute.SYSTEM_PRIVILEGED -> writePrivileged(namespace, key, value)
        SettingWriteRoute.KNOX_DEEP_SETTINGS -> knoxDeepSettings?.takeIf { it.isAvailable() && it.supports(key) }?.write(key, value) == true
        SettingWriteRoute.WRITE_SETTINGS_USER_APPROVAL,
        SettingWriteRoute.DEVICE_OWNER,
        SettingWriteRoute.LAB_ONLY -> false
    }

    private fun writePublic(namespace: SettingNamespace, key: String, value: String): Boolean {
        if (namespace != SettingNamespace.SYSTEM || !Settings.System.canWrite(appContext)) return false
        return Settings.System.putString(resolver, key, value)
    }

    private fun writePrivileged(namespace: SettingNamespace, key: String, value: String): Boolean {
        val secureGranted = appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        return when (namespace) {
            SettingNamespace.SYSTEM -> if (Settings.System.canWrite(appContext) || secureGranted) Settings.System.putString(resolver, key, value) else false
            SettingNamespace.SECURE -> if (secureGranted) Settings.Secure.putString(resolver, key, value) else false
            SettingNamespace.GLOBAL -> if (secureGranted) Settings.Global.putString(resolver, key, value) else false
            else -> false
        }
    }
}
