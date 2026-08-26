package io.dpcaio.offline.android

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import io.dpcaio.offline.OfflineBundleManifest
import java.io.File
import java.util.zip.ZipFile
import java.io.InputStream

data class OfflineInstallSession(
    val parentSessionId: Int?,
    val childSessionIds: List<Int>,
    val atomic: Boolean
)

class AndroidOfflinePackageInstaller(context: Context) {
    private val installer = context.applicationContext.packageManager.packageInstaller

    fun createPackageSession(packageName: String): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_POLICY)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
            }
        }
        return installer.createSession(params)
    }

    fun stageFile(sessionId: Int, name: String, input: InputStream, lengthBytes: Long): Long {
        installer.openSession(sessionId).use { session ->
            return session.openWrite(name, 0, lengthBytes).use { output ->
                val written = input.copyTo(output)
                session.fsync(output)
                written
            }
        }
    }

    fun stageBundle(bundleFile: File, manifest: OfflineBundleManifest): OfflineInstallSession {
        val children = mutableListOf<Int>()
        try {
            ZipFile(bundleFile).use { zip ->
                for (pkg in manifest.packages) {
                    val sessionId = createPackageSession(pkg.packageName)
                    children += sessionId
                    for (file in pkg.files) {
                        val entry = zip.getEntry(file.path) ?: error("Missing bundle entry: ${file.path}")
                        zip.getInputStream(entry).use { input ->
                            stageFile(sessionId, file.path.substringAfterLast('/'), input, entry.size)
                        }
                    }
                }
            }
            return createMultiPackageSession(children)
        } catch (error: Throwable) {
            children.forEach { id -> runCatching { installer.abandonSession(id) } }
            throw error
        }
    }

    fun createMultiPackageSession(childSessionIds: List<Int>): OfflineInstallSession {
        if (childSessionIds.size <= 1 || Build.VERSION.SDK_INT < 29) {
            return OfflineInstallSession(null, childSessionIds, atomic = false)
        }
        val parentParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setMultiPackage()
        }
        val parentId = installer.createSession(parentParams)
        installer.openSession(parentId).use { parent -> childSessionIds.forEach(parent::addChildSessionId) }
        return OfflineInstallSession(parentId, childSessionIds, atomic = true)
    }

    fun commit(plan: OfflineInstallSession, statusReceiver: IntentSender) {
        val id = plan.parentSessionId ?: plan.childSessionIds.single()
        installer.openSession(id).use { it.commit(statusReceiver) }
    }

    fun readback(sessionId: Int): PackageInstaller.SessionInfo? = installer.getSessionInfo(sessionId)
}
