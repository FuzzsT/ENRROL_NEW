package io.dpcaio.app

import android.app.job.JobParameters
import android.app.job.JobService
import io.dpcaio.offline.OfflineStage
import java.io.File

class OfflineRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val store = OfflineDeploymentStore(this)
        val state = store.load() ?: return false
        if (state.stage == OfflineStage.FAILED || state.stage == OfflineStage.OFFLINE_VERIFIED || state.stage == OfflineStage.SYNCED) return false
        if (state.stage == OfflineStage.PACKAGES_INSTALLED) {
            val path = state.bundlePath ?: return false
            Thread {
                runCatching { OfflinePolicyApplier(this).apply(File(path)) }
                    .onFailure { error -> store.save(state.copy(stage = OfflineStage.FAILED, lastError = "OFFLINE_POLICY_PARTIAL:${error.message}")) }
                jobFinished(params, false)
            }.start()
            return true
        }
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
