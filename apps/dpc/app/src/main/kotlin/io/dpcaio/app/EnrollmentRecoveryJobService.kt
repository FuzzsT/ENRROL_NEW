package io.dpcaio.app

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import io.dpcaio.core.model.EnrollmentRetryPolicy

class EnrollmentRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EnrollmentCoordinator.KEY_TRIGGER) ?: "JOB"
        Thread {
            val outcome = EnrollmentCoordinator(this).resumeOrCreate(
                Intent().putExtra(EnrollmentCoordinator.KEY_TRIGGER, trigger)
            )
            if (outcome is EnrollmentOutcome.Retryable) {
                EnrollmentRetryPolicy.delayMillis(outcome.session.retryCount)?.let { delay ->
                    EnrollmentResumeScheduler.schedule(this, "RETRY_${outcome.session.retryCount}", delay)
                }
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
