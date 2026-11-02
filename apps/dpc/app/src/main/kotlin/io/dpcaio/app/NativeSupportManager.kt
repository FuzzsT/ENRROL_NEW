package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import java.io.File

data class NativeSupportState(
    val deviceOwner: Boolean,
    val profileOwner: Boolean,
    val dhizukuInstalled: Boolean,
    val shizukuInstalled: Boolean,
    val appProcessPath: String?,
) {
    val hasPolicyOwner: Boolean get() = deviceOwner || profileOwner
    val hasBinderBridge: Boolean get() = dhizukuInstalled || shizukuInstalled
    val jarCallingAvailable: Boolean get() = appProcessPath != null

    fun summary(): String = buildList {
        if (deviceOwner) add("Device Owner")
        if (profileOwner) add("Profile Owner")
        if (dhizukuInstalled) add("Dhizuku")
        if (shizukuInstalled) add("Shizuku")
        if (appProcessPath != null) add("app_process")
    }.ifEmpty { listOf("standard app APIs") }.joinToString(" • ")
}

object NativeSupportManager {
    private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun probe(context: Context): NativeSupportState {
        val app = context.applicationContext
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        return NativeSupportState(
            deviceOwner = runCatching { dpm?.isDeviceOwnerApp(app.packageName) == true }.getOrDefault(false),
            profileOwner = runCatching { dpm?.isProfileOwnerApp(app.packageName) == true }.getOrDefault(false),
            dhizukuInstalled = isInstalled(app, DHIZUKU_PACKAGE),
            shizukuInstalled = isInstalled(app, SHIZUKU_PACKAGE),
            appProcessPath = detectAppProcess(),
        )
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        true
    }.getOrDefault(false)

    private fun detectAppProcess(): String? = listOf(
        "/system/bin/app_process64",
        "/system/bin/app_process32",
        "/system/bin/app_process",
    ).firstOrNull { path -> File(path).let { it.isFile && it.canExecute() } }
}
