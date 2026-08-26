package io.dpcaio.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Single DeviceAdminReceiver entry point for all DPC-AIO owner/profile modes. */
class AioDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        KnoxStartupController.evaluateAndPersist(context)
        KnoxZtStartupController.enqueue(context)
    }

    override fun onSecurityLogsAvailable(context: Context, intent: Intent) {
        super.onSecurityLogsAvailable(context, intent)
        EnterpriseLogStateStore(context).markSecurityLogsAvailable()
    }

    override fun onNetworkLogsAvailable(
        context: Context,
        intent: Intent,
        batchToken: Long,
        networkLogsCount: Int,
    ) {
        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount)
        EnterpriseLogStateStore(context).recordNetworkBatchToken(batchToken, networkLogsCount)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm.isProfileOwnerApp(context.packageName)) {
            dpm.setProfileEnabled(componentName(context))
        }
        KnoxStartupController.evaluateAndPersist(context)
        KnoxZtStartupController.enqueue(context)
        EnrollmentCoordinator.scheduleResume(context, "PROFILE_PROVISIONING_COMPLETE")
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, AioDeviceAdminReceiver::class.java)
    }
}
