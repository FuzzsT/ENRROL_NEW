package io.dpcaio.app.engineering

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.dpcaio.engineering.AppSigningIdentity
import io.dpcaio.engineering.SigningCertificateIdentity
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class SigningIdentityReader(context: Context) {
    private val packageManager: PackageManager = context.applicationContext.packageManager

    fun readInstalled(packageName: String): SigningIdentityReadResult {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) {
            return SigningIdentityReadResult.Failure(
                SigningIdentityReadError.PACKAGE_NOT_FOUND,
                "Package name is empty",
            )
        }

        val info = try {
            packageInfo(normalized)
        } catch (_: PackageManager.NameNotFoundException) {
            return SigningIdentityReadResult.Failure(
                SigningIdentityReadError.PACKAGE_NOT_FOUND,
                "Package is not installed or not visible: $normalized",
            )
        } catch (error: RuntimeException) {
            return SigningIdentityReadResult.Failure(
                SigningIdentityReadError.SIGNING_INFO_UNAVAILABLE,
                "Package signing information is unavailable: ${error.javaClass.simpleName}",
            )
        }

        val currentSignatures: List<Signature>
        val historySignatures: List<Signature>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
                ?: return SigningIdentityReadResult.Failure(
                    SigningIdentityReadError.SIGNING_INFO_UNAVAILABLE,
                    "PackageInfo.signingInfo is unavailable for $normalized",
                )
            currentSignatures = signingInfo.apkContentsSigners.orEmpty().toList()
            historySignatures = signingInfo.signingCertificateHistory.orEmpty().toList()
        } else {
            @Suppress("DEPRECATION")
            val legacy = info.signatures.orEmpty().toList()
            currentSignatures = legacy
            historySignatures = emptyList()
        }

        if (currentSignatures.isEmpty()) {
            return SigningIdentityReadResult.Failure(
                SigningIdentityReadError.NO_CURRENT_SIGNER,
                "No current signing certificate is available for $normalized",
            )
        }

        val current = currentSignatures.map(::certificateIdentity)
        val currentFingerprints = current.mapTo(linkedSetOf()) { it.sha256Hex }
        val history = historySignatures
            .map(::certificateIdentity)
            .filterNot { it.sha256Hex in currentFingerprints }
            .distinctBy { it.sha256Hex }

        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

        return SigningIdentityReadResult.Success(
            AppSigningIdentity(
                packageName = normalized,
                versionCode = versionCode,
                currentSigners = current,
                signingHistory = history,
            )
        )
    }

    private fun packageInfo(packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }

    private fun certificateIdentity(signature: Signature): SigningCertificateIdentity {
        val der = signature.toByteArray()
        val x509 = parseX509(der)
        return SigningCertificateIdentity(
            derBytes = der.copyOf(),
            sha256Hex = digestHex("SHA-256", der),
            sha1Hex = digestHex("SHA-1", der),
            subjectSummary = x509?.subjectX500Principal?.name,
            serialNumber = x509?.serialNumber?.toString(16)?.uppercase(),
        )
    }

    private fun parseX509(der: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }.getOrNull()

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = when (algorithm) {
            "SHA-256" -> MessageDigest.getInstance("SHA-256")
            "SHA-1" -> MessageDigest.getInstance("SHA-1")
            else -> error("Unsupported digest: $algorithm")
        }
        return digest.digest(bytes).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
        }
    }
}

enum class SigningIdentityReadError {
    PACKAGE_NOT_FOUND,
    SIGNING_INFO_UNAVAILABLE,
    NO_CURRENT_SIGNER,
}

sealed interface SigningIdentityReadResult {
    data class Success(val identity: AppSigningIdentity) : SigningIdentityReadResult
    data class Failure(
        val error: SigningIdentityReadError,
        val message: String,
    ) : SigningIdentityReadResult
}
