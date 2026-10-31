package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppLockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            try {
                val coordinator = AppLockCoordinator(context)
                when (intent?.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> coordinator.restorePendingRelocks()
                    else -> coordinator.reconcileExpiredUnlocks()
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_RELOCK = "io.dpcaio.app.action.APP_LOCK_RELOCK"
        const val EXTRA_TARGET_PACKAGE = "app_lock_target_package"
    }
}
