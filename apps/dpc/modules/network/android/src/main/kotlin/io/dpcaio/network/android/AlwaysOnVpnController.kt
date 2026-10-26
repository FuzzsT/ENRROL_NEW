package io.dpcaio.network.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class AlwaysOnVpnController(context: Context, private val admin: ComponentName) {
    private val dpm = context.applicationContext.getSystemService(DevicePolicyManager::class.java)

    fun set(packageName: String?, lockdown: Boolean): Boolean = runCatching {
        dpm.setAlwaysOnVpnPackage(admin, packageName, lockdown)
        dpm.getAlwaysOnVpnPackage(admin) == packageName
    }.getOrDefault(false)

    fun currentPackage(): String? = dpm.getAlwaysOnVpnPackage(admin)
}
