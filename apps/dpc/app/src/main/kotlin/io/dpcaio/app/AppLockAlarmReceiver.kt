package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppLockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val packageName = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val pending = goAsync()
        Thread {
            try {
                AppLockCoordinator(context).reconcileExpiredUnlocks()
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "app_lock_target_package"
    }
}
