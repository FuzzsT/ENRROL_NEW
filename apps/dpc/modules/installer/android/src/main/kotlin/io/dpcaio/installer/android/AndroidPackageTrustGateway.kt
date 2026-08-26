package io.dpcaio.installer.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.installer.InstallSourceConfidence
import io.dpcaio.installer.PackageTrustExpectation
import io.dpcaio.installer.PackageTrustObservation
import io.dpcaio.installer.PackageTrustPlanner
import io.dpcaio.installer.PackageTrustSnapshot
import java.security.MessageDigest

class AndroidPackageTrustGateway(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val planner = PackageTrustPlanner()

    fun inspect(
        expectation: PackageTrustExpectation,
        transportSha256: Map<String, String> = emptyMap(),
        runtimeMerkleChecksums: Map<String, String> = emptyMap(),
    ): PackageTrustSnapshot {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(expectation.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(expectation.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val signingInfo = info.signingInfo
        val current = signingInfo?.apkContentsSigners.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }
        val history = signingInfo?.signingCertificateHistory.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }
        val installSource = if (Build.VERSION.SDK_INT >= 30) runCatching { pm.getInstallSourceInfo(expectation.packageName) }.getOrNull() else null
        val installing = if (Build.VERSION.SDK_INT >= 30) installSource?.installingPackageName else null
        val initiating = if (Build.VERSION.SDK_INT >= 30) installSource?.initiatingPackageName else null
        val confidence = when {
            initiating == appContext.packageName || installing == appContext.packageName -> InstallSourceConfidence.CONFIRMED
            initiating != null || installing != null -> InstallSourceConfidence.PARTIAL
            else -> InstallSourceConfidence.UNKNOWN
        }
        val observation = PackageTrustObservation(
            packageName = info.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            currentSignerSha256 = current,
            signerLineageSha256 = history,
            hasMultipleSigners = signingInfo?.hasMultipleSigners() == true,
            installedSplits = info.splitNames.orEmpty().toSet(),
            installSourceConfidence = confidence,
            installingPackageName = installing,
            initiatingPackageName = initiating,
            runtimeMerkleChecksums = runtimeMerkleChecksums,
            transportSha256 = transportSha256,
        )
        return planner.evaluate(expectation, observation)
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02X".format(it) }
}
