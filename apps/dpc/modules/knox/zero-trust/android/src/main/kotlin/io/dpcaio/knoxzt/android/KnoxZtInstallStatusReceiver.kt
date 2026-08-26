package io.dpcaio.knoxzt.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class KnoxZtInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        context.getSharedPreferences("knoxzt_install_status", Context.MODE_PRIVATE).edit()
            .putInt("status", status)
            .putString("message", message)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        context.sendBroadcast(Intent(ACTION_INSTALL_RESULT).apply {
            setPackage(context.packageName)
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message)
        })
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.dpcaio.knoxzt.action.PACKAGE_INSTALL_STATUS"
        const val ACTION_INSTALL_RESULT = "io.dpcaio.knoxzt.action.PACKAGE_INSTALL_RESULT"
    }
}
