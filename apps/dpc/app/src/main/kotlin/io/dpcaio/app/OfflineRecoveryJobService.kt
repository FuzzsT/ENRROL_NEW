package io.dpcaio.app

import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import io.dpcaio.offline.OfflineStage
import io.dpcaio.offline.android.AndroidOfflinePackageInstaller
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

        if (state.stage == OfflineStage.PACKAGES_STAGED) {
            val generation = synchronized(lifecycleLock) {
                worker?.interrupt()
                workGeneration.incrementAndGet()
            }
            val thread = Thread {
                if (generation != workGeneration.get()) return@Thread
                val commitSessionId = state.commitSessionId
                val result = runCatching {
                    if (commitSessionId == null) {
                        if (generation != workGeneration.get()) return@runCatching
                        store.save(
                            state.copy(
                                stage = OfflineStage.FAILED,
                                parentSessionId = null,
                                commitSessionId = null,
                                lastError = "PACKAGE_SESSION_RECOVERY_UNAVAILABLE",
                            )
                        )
                        return@runCatching
                    }

                    val packageInstaller = AndroidOfflinePackageInstaller(this)
                    val committed = packageInstaller.isCommitted(commitSessionId)
                    if (generation != workGeneration.get()) return@runCatching
                    if (committed == null) {
                        store.save(
                            state.copy(
                                stage = OfflineStage.FAILED,
                                parentSessionId = null,
                                commitSessionId = null,
                                lastError = "PACKAGE_SESSION_MISSING:$commitSessionId",
                            )
                        )
                        return@runCatching
                    }

                    if (!committed) {
                        val callback = PendingIntent.getBroadcast(
                            this,
                            4101,
                            Intent(this, OfflineInstallStatusReceiver::class.java)
                                .setAction(OfflineInstallStatusReceiver.ACTION_INSTALL_RESULT),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        if (generation != workGeneration.get()) return@runCatching
                        packageInstaller.commitSession(commitSessionId, callback.intentSender)
                    }
                }

                synchronized(lifecycleLock) {
                    if (generation != workGeneration.get()) return@synchronized
                    result.onFailure { error ->
                        val latest = store.load() ?: state
                        store.save(
                            latest.copy(
                                stage = OfflineStage.FAILED,
                                parentSessionId = null,
                                commitSessionId = null,
                                lastError = "PACKAGE_SESSION_RECOVERY_FAILED:${error.message}",
                            )
                        )
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

        val policyRecoveryStage = state.stage in setOf(
            OfflineStage.PACKAGES_INSTALLED,
            OfflineStage.PERMISSIONS_APPLIED,
            OfflineStage.COMPONENTS_APPLIED,
            OfflineStage.POLICIES_APPLIED,
            OfflineStage.READBACK_VERIFIED,
        )
        if (policyRecoveryStage) {
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
                        val latest = store.load() ?: state
                        store.save(latest.copy(stage = OfflineStage.FAILED, lastError = "OFFLINE_POLICY_PARTIAL:${error.message}"))
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
