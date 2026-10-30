package io.dpcaio.selfupdate.android

import android.content.Context
import android.content.IntentSender
import io.dpcaio.installer.PackageSource
import io.dpcaio.installer.android.AndroidPackageInstallerAdapter
import io.dpcaio.installer.android.InstallSessionSpec
import io.dpcaio.selfupdate.SelfUpdateInstallPolicy

data class SelfUpdateInstallSubmission(
    val submitted: Boolean,
    val sessionId: Int? = null,
    val message: String,
)

class SelfUpdateInstaller(context: Context) {
    private val applicationContext = context.applicationContext
    private val adapter = AndroidPackageInstallerAdapter(applicationContext)

    fun submit(
        candidate: StoredSelfUpdateCandidate,
        policy: SelfUpdateInstallPolicy,
        statusReceiver: IntentSender,
    ): SelfUpdateInstallSubmission {
        if (!candidate.verified || !candidate.apkFile.isFile) {
            return SelfUpdateInstallSubmission(false, message = "CANDIDATE_NOT_VERIFIED")
        }

        val requireUserAction = when (policy) {
            SelfUpdateInstallPolicy.ASK_ALWAYS -> true
            SelfUpdateInstallPolicy.SILENT_IF_ALLOWED,
            SelfUpdateInstallPolicy.AUTO_SILENT -> false
        }

        val created = adapter.createSession(
            InstallSessionSpec(
                packageName = candidate.manifest.packageName,
                installerPackageName = applicationContext.packageName,
                packageSource = PackageSource.DOWNLOADED_FILE,
                requireUserAction = requireUserAction,
            )
        )
        val sessionId = created.value
            ?: return SelfUpdateInstallSubmission(false, message = "SESSION_CREATE_FAILED: ${created.status}: ${created.message}")

        return try {
            val staged = candidate.apkFile.inputStream().use { input ->
                adapter.stageSingleApk(sessionId, input, candidate.apkFile.length())
            }
            if (!staged.isSuccess) {
                runCatching { adapter.abandon(sessionId) }
                return SelfUpdateInstallSubmission(false, sessionId, "STAGE_FAILED: ${staged.status}: ${staged.message}")
            }

            val committed = adapter.commit(sessionId, statusReceiver)
            if (!committed.isSuccess) {
                runCatching { adapter.abandon(sessionId) }
                return SelfUpdateInstallSubmission(false, sessionId, "COMMIT_FAILED: ${committed.status}: ${committed.message}")
            }

            SelfUpdateInstallSubmission(
                submitted = true,
                sessionId = sessionId,
                message = "INSTALL_SUBMITTED",
            )
        } catch (error: Exception) {
            runCatching { adapter.abandon(sessionId) }
            SelfUpdateInstallSubmission(
                submitted = false,
                sessionId = sessionId,
                message = "INSTALL_SESSION_EXCEPTION: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }
}
