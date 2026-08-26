package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EnrollmentRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "UNKNOWN"
        EnrollmentCoordinator.scheduleResume(context, action)
    }
}
