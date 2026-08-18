package io.dpcaio.knoxzt.android

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.dpcaio.installer.PackageSource
import io.dpcaio.installer.android.AndroidPackageInstallerAdapter
import io.dpcaio.installer.android.InstallSessionSpec
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import io.dpcaio.knoxzt.KnoxZtRecoveryPlanner
import io.dpcaio.knoxzt.KnoxZtRecoveryRoute
import io.dpcaio.shizuku.ShizukuUserServiceClient

enum class KnoxZtRuntimeStatus {
    READY,
    ENABLED,
    RESTORED_EXISTING,
    INSTALL_SUBMITTED,
    TRUSTED_SOURCE_REQUIRED,
    UNTRUSTED_PACKAGE,
    FAILED
}

data class KnoxZtRecoveryResult(
    val status: KnoxZtRuntimeStatus,
    val detail: String,
    val attemptedRoutes: List<KnoxZtRecoveryRoute>
)

class KnoxZtRecoveryManager(
    context: Context,
    private val admin: ComponentName,
    private val sourceStore: KnoxZtInstallSourceStore = KnoxZtInstallSourceStore(context),
    private val shizuku: ShizukuUserServiceClient? = null,
    private val userId: Int = android.os.UserHandle.myUserId()
) {
    private val app = context.applicationContext
    private val dpm = app.getSystemService(DevicePolicyManager::class.java)
    private val inspector = KnoxZtPackageInspector(app)
    private val planner = KnoxZtRecoveryPlanner()

    fun ensureReady(): KnoxZtRecoveryResult {
        val source = sourceStore.load()
        val plan = planner.plan(inspector.inspect(), source != null)
        if (plan.blockers.isNotEmpty()) {
            return KnoxZtRecoveryResult(
                if (plan.blockers.contains("TRUSTED_INSTALL_SOURCE_REQUIRED")) KnoxZtRuntimeStatus.TRUSTED_SOURCE_REQUIRED else KnoxZtRuntimeStatus.UNTRUSTED_PACKAGE,
                plan.blockers.joinToString(","),
                emptyList()
            )
        }
        val attempts = mutableListOf<KnoxZtRecoveryRoute>()
        for (route in plan.routes) {
            attempts += route
            when (route) {
                KnoxZtRecoveryRoute.NONE -> return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.READY, "KnoxZT already ready", attempts)
                KnoxZtRecoveryRoute.ENABLE_SYSTEM_APP -> {
                    var ok = runCatching { dpm.enableSystemApp(admin, KNOXZT_PACKAGE); inspector.inspect().enabled }.getOrDefault(false)
                    if (!ok) {
                        shizuku?.bind()
                        ok = shizuku?.setPackageEnabled(KNOXZT_PACKAGE, true, userId) == 0 && inspector.inspect().enabled
                    }
                    if (ok) return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.ENABLED, "KnoxZT enabled", attempts)
                }
                KnoxZtRecoveryRoute.INSTALL_EXISTING_PACKAGE -> {
                    var restored = runCatching { dpm.installExistingPackage(admin, KNOXZT_PACKAGE) }.getOrDefault(false)
                    if (!restored) {
                        shizuku?.bind()
                        restored = shizuku?.installExistingPackage(KNOXZT_PACKAGE, userId) == 0
                    }
                    if (!restored) continue
                }
                KnoxZtRecoveryRoute.DOWNLOAD_VERIFY_INSTALL -> {
                    val trusted = source ?: return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.TRUSTED_SOURCE_REQUIRED, "No trusted KnoxZT source", attempts)
                    return submitTrustedDownload(trusted, attempts)
                }
            }
        }
        val finalProbe = inspector.inspect()
        if (finalProbe.installedForUser) {
            var enabled = runCatching { dpm.enableSystemApp(admin, KNOXZT_PACKAGE); inspector.inspect().enabled }.getOrDefault(false)
            if (!enabled) {
                shizuku?.bind()
                enabled = shizuku?.setPackageEnabled(KNOXZT_PACKAGE, true, userId) == 0 && inspector.inspect().enabled
            }
            if (enabled) return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.RESTORED_EXISTING, "KnoxZT restored and enabled", attempts)
        }
        return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.FAILED, "KnoxZT recovery routes exhausted", attempts)
    }

    private fun submitTrustedDownload(source: KnoxZtInstallSource, attempts: List<KnoxZtRecoveryRoute>): KnoxZtRecoveryResult {
        return try {
            val apk = KnoxZtDownloader(app).download(source)
            if (!KnoxZtApkVerifier(app).verify(apk, source)) {
                return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.UNTRUSTED_PACKAGE, "Downloaded KnoxZT APK failed package/hash/signature verification", attempts)
            }
            val installer = AndroidPackageInstallerAdapter(app)
            val session = installer.createSession(
                InstallSessionSpec(
                    packageName = KNOXZT_PACKAGE,
                    packageSource = PackageSource.DOWNLOADED_FILE,
                    requireUserAction = false
                )
            )
            val sessionId = session.value ?: return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.FAILED, session.message ?: "Session creation failed", attempts)
            apk.inputStream().use { input ->
                val staged = installer.stageSingleApk(sessionId, input, apk.length())
                if (!staged.isSuccess) return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.FAILED, staged.message ?: "Staging failed", attempts)
            }
            val statusIntent = Intent(app, KnoxZtInstallStatusReceiver::class.java).apply {
                action = KnoxZtInstallStatusReceiver.ACTION_INSTALL_STATUS
            }
            val pending = PendingIntent.getBroadcast(
                app,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val committed = installer.commit(sessionId, pending.intentSender)
            if (!committed.isSuccess) return KnoxZtRecoveryResult(KnoxZtRuntimeStatus.FAILED, committed.message ?: "Commit failed", attempts)
            KnoxZtRecoveryResult(KnoxZtRuntimeStatus.INSTALL_SUBMITTED, "Verified KnoxZT install submitted", attempts)
        } catch (e: Exception) {
            KnoxZtRecoveryResult(KnoxZtRuntimeStatus.FAILED, e.message ?: e.javaClass.simpleName, attempts)
        }
    }
}
