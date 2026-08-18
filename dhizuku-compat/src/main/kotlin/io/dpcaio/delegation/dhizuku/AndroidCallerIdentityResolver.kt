package io.dpcaio.delegation.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import io.dpcaio.delegation.ClientIdentity
import java.security.MessageDigest

class AndroidCallerIdentityResolver(context: Context) {
    private val pm = context.applicationContext.packageManager

    fun resolve(uid: Int): List<ClientIdentity> {
        val packages = pm.getPackagesForUid(uid)?.toList().orEmpty()
        val userId = UserHandle.getUserId(uid)
        return packages.mapNotNull { packageName ->
            val digest = signingDigest(packageName) ?: return@mapNotNull null
            ClientIdentity(packageName, uid, userId, digest)
        }
    }

    private fun signingDigest(packageName: String): String? = try {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        val certificate = signers.firstOrNull()?.toByteArray() ?: return null
        MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { "%02X".format(it) }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
