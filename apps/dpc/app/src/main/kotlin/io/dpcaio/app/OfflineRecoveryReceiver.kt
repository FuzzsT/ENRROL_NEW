package io.dpcaio.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class OfflineRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = OfflineDeploymentStore(context).load() ?: return
        if (state.stage.name in setOf("OFFLINE_VERIFIED", "SYNCED")) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, OfflineRecoveryJobService::class.java))
            .setMinimumLatency(1_000L)
            .setOverrideDeadline(30_000L)
            .build()
        scheduler.schedule(job)
    }

    companion object { const val JOB_ID = 0xD10F }
}
