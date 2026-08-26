package io.dpcaio.network.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class DeviceOwnerPrivateDnsController(context: Context, private val admin: ComponentName) {
    private val dpm = context.applicationContext.getSystemService(DevicePolicyManager::class.java)
    fun applySpecifiedHost(host: String): Int = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, host)
    fun applyOpportunistic(): Int = dpm.setGlobalPrivateDnsModeOpportunistic(admin)
    fun readHost(): String? = dpm.getGlobalPrivateDnsHost(admin)
}
