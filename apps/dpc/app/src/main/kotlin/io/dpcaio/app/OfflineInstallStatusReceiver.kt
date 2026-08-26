package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.dpcaio.installer.PackageTrustExpectation
import io.dpcaio.installer.android.AndroidPackageTrustGateway
import io.dpcaio.offline.OfflineStage
import io.dpcaio.offline.android.AndroidOfflineBundleReader
import java.io.File

class OfflineInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = OfflineDeploymentStore(context)
        val state = store.load() ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                val trust = verifyInstalledPackages(context, state)
                if (!trust.first) {
                    store.save(state.copy(stage = OfflineStage.FAILED, parentSessionId = null, lastError = "PACKAGE_TRUST_MISMATCH:${trust.second}"))
                    return
                }
                store.save(state.copy(stage = OfflineStage.PACKAGES_INSTALLED, parentSessionId = null, lastError = null))
                context.sendBroadcast(Intent(context, OfflineRecoveryReceiver::class.java).setAction(ACTION_PACKAGE_DONE))
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                store.save(state.copy(lastError = "USER_ACTION_REQUIRED"))
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "PACKAGE_INSTALL_FAILED:$status"
                store.save(state.copy(stage = OfflineStage.FAILED, lastError = message))
            }
        }
    }

    private fun verifyInstalledPackages(context: Context, state: OfflineDeploymentState): Pair<Boolean, String> {
        val bundlePath = state.bundlePath ?: return false to "BUNDLE_PATH_MISSING"
        val bundle = File(bundlePath)
        val inspected = AndroidOfflineBundleReader(context).inspect(bundle, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)
        val manifest = inspected.manifest ?: return false to "MANIFEST_UNAVAILABLE:${inspected.detail}"
        if (!inspected.ready) return false to "BUNDLE_NOT_TRUSTED:${inspected.detail}"
        val gateway = AndroidPackageTrustGateway(context)
        val failures = mutableListOf<String>()
        for (pkg in manifest.packages) {
            val expectedSplits = pkg.requiredSplits.mapNotNull(::expectedSplitName).toSet()
            val expectation = PackageTrustExpectation(
                packageName = pkg.packageName,
                versionCode = pkg.versionCode,
                signerSha256 = setOf(pkg.signingCertificateSha256),
                requiredSplits = expectedSplits,
            )
            val transport = pkg.files.associate { it.path.substringAfterLast('/') to it.sha256 }
            val snapshot = runCatching { gateway.inspect(expectation, transportSha256 = transport) }.getOrElse {
                failures += "${pkg.packageName}:TRUST_READBACK_FAILED:${it.javaClass.simpleName}"
                continue
            }
            if (!snapshot.acceptedForOffline) {
                failures += "${pkg.packageName}:${snapshot.issues.sortedBy { it.name }.joinToString(",")}" 
            }
        }
        return (failures.isEmpty()) to failures.joinToString(";")
    }

    private fun expectedSplitName(path: String): String? {
        val file = path.substringAfterLast('/')
        if (file == "base.apk" || !file.endsWith(".apk")) return null
        return file.removeSuffix(".apk").removePrefix("split_")
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "io.dpcaio.offline.INSTALL_RESULT"
        const val ACTION_PACKAGE_DONE = "io.dpcaio.offline.PACKAGES_INSTALLED"
    }
}
