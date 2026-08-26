package io.dpcaio.knox.mock.android

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import io.dpcaio.knox.mock.KnoxMockGateway

class KnoxMockService : Service() {
    private val gateway = KnoxMockGateway()

    private val binder = object : IKnoxMockService.Stub() {
        override fun getLicenseState(): String {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val realOwner = dpm.isDeviceOwnerApp(packageName)
            return gateway.licenseState(labGateActive = true, realKnoxActive = false).name +
                if (realOwner) ":DEVICE_OWNER" else ":NO_DEVICE_OWNER"
        }

        override fun setPackageHidden(packageName: String, hidden: Boolean): String =
            runDpm(packageName) { dpm, admin ->
                val accepted = dpm.setApplicationHidden(admin, packageName, hidden)
                val verified = accepted && dpm.isApplicationHidden(admin, packageName) == hidden
                if (verified) "GREEN_DPM_VERIFIED" else "FAILED_VERIFICATION"
            }

        override fun setPackageSuspended(packageName: String, suspended: Boolean): String =
            runDpm(packageName) { dpm, admin ->
                val failed = dpm.setPackagesSuspended(admin, arrayOf(packageName), suspended)
                val verified = failed.isEmpty() && dpm.isPackageSuspended(admin, packageName) == suspended
                if (verified) "GREEN_DPM_VERIFIED" else "FAILED_VERIFICATION"
            }
    }

    private fun runDpm(target: String, action: (DevicePolicyManager, ComponentName) -> String): String = try {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(packageName)) return "NOT_DEVICE_OWNER"
        val admin = ComponentName(this, "io.dpcaio.app.AioDeviceAdminReceiver")
        action(dpm, admin)
    } catch (e: SecurityException) {
        "SECURITY_EXCEPTION:${e.javaClass.simpleName}"
    } catch (e: Exception) {
        "FAILED:${e.javaClass.simpleName}:$target"
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
