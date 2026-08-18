package io.dpcaio.knoxzt.android

import android.content.Context
import android.content.pm.PackageManager
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import java.io.File
import java.security.MessageDigest

class KnoxZtApkVerifier(context: Context) {
    private val pm = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun verify(apk: File, source: KnoxZtInstallSource): Boolean {
        if (!apk.isFile) return false
        if (!digest(apk.readBytes()).equals(source.apkSha256.normalizeDigest(), ignoreCase = true)) return false

        val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (info.packageName != KNOXZT_PACKAGE) return false
        val signing = info.signingInfo ?: return false
        val signers = signing.apkContentsSigners.map { digest(it.toByteArray()) }.toSet()
        val allowed = source.signerSha256.map { it.normalizeDigest() }.toSet()
        return signers.isNotEmpty() && signers.all { it in allowed }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun String.normalizeDigest(): String = lowercase().replace(":", "").replace(" ", "")
}
