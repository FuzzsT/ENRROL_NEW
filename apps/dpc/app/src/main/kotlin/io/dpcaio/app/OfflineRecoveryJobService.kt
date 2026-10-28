package io.dpcaio.app

import android.app.job.JobParameters
import android.app.job.JobService
import io.dpcaio.offline.OfflineStage
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class OfflineRecoveryJobService : JobService() {
    private val lifecycleLock = Any()
    private val workGeneration = AtomicLong(0)
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val store = OfflineDeploymentStore(this)
        val state = store.load() ?: return false
        if (state.stage == OfflineStage.FAILED || state.stage == OfflineStage.OFFLINE_VERIFIED || state.stage == OfflineStage.SYNCED) return false
        if (state.stage == OfflineStage.PACKAGES_INSTALLED) {
            val path = state.bundlePath ?: return false
            val generation = synchronized(lifecycleLock) {
                worker?.interrupt()
                workGeneration.incrementAndGet()
            }
            val thread = Thread {
                if (generation != workGeneration.get()) return@Thread
                val result = runCatching { OfflinePolicyApplier(this).apply(File(path)) }
                synchronized(lifecycleLock) {
                    if (generation != workGeneration.get()) return@synchronized
                    result.onFailure { error ->
                        store.save(state.copy(stage = OfflineStage.FAILED, lastError = "OFFLINE_POLICY_PARTIAL:${error.message}"))
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
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = synchronized(lifecycleLock) {
        workGeneration.incrementAndGet()
        worker?.interrupt()
        worker = null
        true
    }
}
