package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.dpcaio.knoxzt.android.KnoxZtInstallStatusReceiver

class KnoxZtRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == KnoxZtInstallStatusReceiver.ACTION_INSTALL_RESULT) {
            KnoxZtStartupController.enqueue(context)
        }
    }
}
