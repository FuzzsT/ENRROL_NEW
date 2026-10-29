package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class AppInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "unknown"
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val queueItemId = intent.getStringExtra(QUEUE_ITEM_ID)
        val sessionId = intent.getIntExtra(SESSION_ID, -1).takeIf { it >= 0 }

        if (queueItemId != null) {
            InstallQueueCoordinator(InstallQueueStore(context)).onPackageInstallerResult(
                itemId = queueItemId,
                sessionId = sessionId,
                status = status,
                packageName = packageName,
                detail = message,
            )
        }

        val summary = when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                "Install completed\npackage=$packageName"
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmationIntent(intent)?.let { confirmation ->
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                }
                "Waiting for Android install confirmation\npackage=$packageName"
            }
            else -> {
                "Install failed\nstatus=$status\npackage=$packageName\nmessage=${message ?: "unknown"}"
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUMMARY, summary)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()

        context.sendBroadcast(Intent(ACTION_QUEUE_CHANGED).setPackage(context.packageName))
    }

    private fun confirmationIntent(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "io.dpcaio.app.PACKAGE_INSTALL_RESULT"
        const val ACTION_QUEUE_CHANGED = "io.dpcaio.app.PACKAGE_INSTALL_QUEUE_CHANGED"
        const val QUEUE_ITEM_ID = "queue_item_id"
        const val SESSION_ID = "session_id"
        const val PREFS = "dpc-aio-app-installer-status"
        const val KEY_SUMMARY = "summary"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
