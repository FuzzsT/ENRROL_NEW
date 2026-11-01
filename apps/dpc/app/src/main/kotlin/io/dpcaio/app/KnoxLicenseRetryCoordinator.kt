package io.dpcaio.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class KnoxLicenseRetryCoordinator(private val context: Context) {
    private val scheduler: JobScheduler? = context.getSystemService(JobScheduler::class.java)

    fun scheduleForRetry(snapshot: KnoxLicenseActivationSnapshot): Boolean {
        if (snapshot.status != KnoxLicenseActivationStatus.RETRYABLE) return false
        if (!KnoxProvisioningSecretCapture.isAutoActivateRequested(context)) return false
        val jobs = scheduler ?: return false
        if (jobs.getPendingJob(JOB_ID) != null) return true

        val info = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, KnoxLicenseRetryJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()

        return jobs.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    fun isScheduled(): Boolean = scheduler?.getPendingJob(JOB_ID) != null

    fun onBootOrPackageReplace(): Boolean {
        val snapshot = KnoxLicenseRuntimeStore(context).read() ?: return false
        return scheduleForRetry(snapshot)
    }

    fun cancel() {
        scheduler?.cancel(JOB_ID)
    }

    companion object {
        private const val JOB_ID = 0x4B5045
        private const val RETRY_BACKOFF_MS = 15L * 60L * 1000L
    }
}

class KnoxLicenseRetryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        Thread {
            val outcome = KnoxLicenseController(context)
                .activateIfRequested(KnoxLicenseAttemptSource.RETRY_JOB)
            jobFinished(params, outcome.status == KnoxLicenseActivationStatus.RETRYABLE)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
