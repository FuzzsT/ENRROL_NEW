package io.dpcaio.app

import android.app.admin.DeviceAdminReceiver
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

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        KnoxStartupController.evaluateAndPersist(context)
        KnoxZtStartupController.enqueue(context)
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, AioDeviceAdminReceiver::class.java)
    }
}
