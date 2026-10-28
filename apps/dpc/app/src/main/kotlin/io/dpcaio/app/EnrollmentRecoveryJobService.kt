package io.dpcaio.app

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import io.dpcaio.core.model.EnrollmentRetryPolicy
import java.util.concurrent.atomic.AtomicLong

class EnrollmentRecoveryJobService : JobService() {
    private val lifecycleLock = Any()
    private val workGeneration = AtomicLong(0)
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EnrollmentCoordinator.KEY_TRIGGER) ?: "JOB"
        val generation = synchronized(lifecycleLock) {
            worker?.interrupt()
            workGeneration.incrementAndGet()
        }
        val thread = Thread {
            if (generation != workGeneration.get()) return@Thread
            val result = runCatching {
                EnrollmentCoordinator(this).resumeOrCreate(
                    Intent().putExtra(EnrollmentCoordinator.KEY_TRIGGER, trigger)
                )
            }
            synchronized(lifecycleLock) {
                if (generation != workGeneration.get()) return@synchronized
                val outcome = result.getOrElse {
                    worker = null
                    jobFinished(params, true)
                    return@synchronized
                }
                if (outcome is EnrollmentOutcome.Retryable) {
                    EnrollmentRetryPolicy.delayMillis(outcome.session.retryCount)?.let { delay ->
                        if (generation != workGeneration.get()) return@synchronized
                        EnrollmentResumeScheduler.schedule(this, "RETRY_${outcome.session.retryCount}", delay)
                    }
                }
                if (generation != workGeneration.get()) return@synchronized
                worker = null
                jobFinished(params, false)
            }
        }
        synchronized(lifecycleLock) {
            if (generation != workGeneration.get()) return true
            worker = thread
        }
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = synchronized(lifecycleLock) {
        workGeneration.incrementAndGet()
        worker?.interrupt()
        worker = null
        true
    }
}
