package io.dpcaio.selfupdate.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.selfupdate.SelfUpdateManifest
import java.io.File
import java.security.MessageDigest

data class SelfUpdateVerificationResult(
    val accepted: Boolean,
    val reason: String,
    val observedSha256: String? = null,
    val observedPackageName: String? = null,
    val observedVersionCode: Long? = null,
)

class SelfUpdateVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun verify(
        apkFile: File,
        manifest: SelfUpdateManifest,
        installedPackageName: String,
    ): SelfUpdateVerificationResult {
        if (!apkFile.isFile) return reject("APK_MISSING")
        manifest.apkSize?.let { expected ->
            if (apkFile.length() != expected) return reject("SIZE_MISMATCH")
        }

        val sha256 = sha256(apkFile)
        if (!sha256.equals(manifest.apkSha256, ignoreCase = true)) {
            return reject("SHA256_MISMATCH", sha256)
        }

        val incoming = archiveInfo(apkFile) ?: return reject("APK_PARSE_FAILED", sha256)
        val incomingVersion = versionCode(incoming)
        if (incoming.packageName != manifest.packageName || incoming.packageName != installedPackageName) {
            return reject("PACKAGE_MISMATCH", sha256, incoming.packageName, incomingVersion)
        }
        if (incomingVersion != manifest.versionCode) {
            return reject("VERSION_MISMATCH", sha256, incoming.packageName, incomingVersion)
        }

        val installed = installedInfo(installedPackageName)
            ?: return reject("INSTALLED_PACKAGE_MISSING", sha256, incoming.packageName, incomingVersion)
        val installedVersion = versionCode(installed)
        if (incomingVersion <= installedVersion) {
            return reject("DOWNGRADE_OR_SAME_VERSION", sha256, incoming.packageName, incomingVersion)
        }

        val installedCurrent = currentSignerDigests(installed)
        val incomingLineage = signerLineageDigests(incoming)
        if (installedCurrent.isEmpty() || incomingLineage.isEmpty() || !installedCurrent.all { it in incomingLineage }) {
            return reject("SIGNER_MISMATCH", sha256, incoming.packageName, incomingVersion)
        }

        return SelfUpdateVerificationResult(
            accepted = true,
            reason = "VERIFIED",
            observedSha256 = sha256,
            observedPackageName = incoming.packageName,
            observedVersionCode = incomingVersion,
        )
    }

    private fun archiveInfo(file: File): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    private fun installedInfo(packageName: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    private fun currentSignerDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= 28) {
            return info.signingInfo?.apkContentsSigners.orEmpty()
                .mapTo(linkedSetOf()) { digest(it.toByteArray()) }
        }
        @Suppress("DEPRECATION")
        return info.signatures.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }
    }

    private fun signerLineageDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptySet()
            return (
                signing.apkContentsSigners.orEmpty().asSequence() +
                    signing.signingCertificateHistory.orEmpty().asSequence()
                ).mapTo(linkedSetOf()) { digest(it.toByteArray()) }
        }
        @Suppress("DEPRECATION")
        return info.signatures.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) md.update(buffer, 0, count)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun reject(
        reason: String,
        sha256: String? = null,
        packageName: String? = null,
        versionCode: Long? = null,
    ) = SelfUpdateVerificationResult(
        accepted = false,
        reason = reason,
        observedSha256 = sha256,
        observedPackageName = packageName,
        observedVersionCode = versionCode,
    )
}
