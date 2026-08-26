package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class KnoxBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KnoxStartupController.evaluateAndPersist(context)
        KnoxZtStartupController.enqueue(context)
    }
}
