package io.dpcaio.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

object EnrollmentResumeScheduler {
    fun schedule(context: Context, trigger: String, delayMillis: Long = 0L) {
        if (EnrollmentSessionStore(context).read() == null) return
        val component = ComponentName(context, EnrollmentRecoveryJobService::class.java)
        val builder = JobInfo.Builder(JOB_ID, component)
            .setPersisted(true)
            .setMinimumLatency(delayMillis.coerceAtLeast(0L))
            .setExtras(PersistableBundle().apply { putString(EnrollmentCoordinator.KEY_TRIGGER, trigger) })
        if (!EnrollmentSessionStore(context).read()?.serverUri.isNullOrBlank()) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        }
        context.getSystemService(JobScheduler::class.java).schedule(builder.build())
    }

    private const val JOB_ID = 0x44504309
}
