package io.dpcaio.installer.android

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.installer.PackageSource
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import java.io.File
import java.io.InputStream


enum class InstallPermissionState {
    DEFAULT,
    DENIED,
    GRANTED
}

data class InstallSessionSpec(
    val packageName: String,
    val installerPackageName: String? = null,
    val packageSource: PackageSource = PackageSource.UNSPECIFIED,
    val originatingUid: Int? = null,
    val requireUserAction: Boolean? = null,
    val requestUpdateOwnership: Boolean = false,
    val permissionStates: Map<String, InstallPermissionState> = emptyMap(),
    val installReason: Int = PackageManager.INSTALL_REASON_POLICY
) {
    constructor(
        packageName: String,
        installerPackageName: String?,
        packageSource: PackageSource,
        requireUserAction: Boolean,
    ) : this(
        packageName = packageName,
        installerPackageName = installerPackageName,
        packageSource = packageSource,
        originatingUid = null,
        requireUserAction = requireUserAction,
    )
}

class AndroidPackageInstallerAdapter(context: Context) {
    private val installer = context.applicationContext.packageManager.packageInstaller

    fun createSession(spec: InstallSessionSpec): PolicyResult<Int> {
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(spec.packageName)
                setInstallReason(spec.installReason)
                spec.originatingUid?.let(::setOriginatingUid)

                if (Build.VERSION.SDK_INT >= 31) {
                    spec.requireUserAction?.let {
                        setRequireUserAction(
                            if (it) PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                            else PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    setPackageSource(spec.packageSource.toPlatformSource())
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    spec.installerPackageName?.let { setInstallerPackageName(it) }
                    if (spec.requestUpdateOwnership) setRequestUpdateOwnership(true)
                    spec.permissionStates.forEach { (permission, state) ->
                        setPermissionState(permission, state.toPlatformState())
                    }
                }
            }
            PolicyResult.success(installer.createSession(params))
        } catch (e: SecurityException) {
            PolicyResult.failure(
                status = PolicyStatus.SECURITY_EXCEPTION,
                message = e.message ?: "PackageInstaller session denied",
                errorType = e.javaClass.name
            )
        } catch (e: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = e.message ?: "PackageInstaller session creation failed",
                errorType = e.javaClass.name
            )
        }
    }

    fun stageSingleApk(sessionId: Int, input: InputStream, lengthBytes: Long): PolicyResult<Long> {
        return try {
            installer.openSession(sessionId).use { session ->
                val written = session.openWrite("base.apk", 0, lengthBytes).use { output ->
                    val bytes = input.copyTo(output)
                    session.fsync(output)
                    bytes
                }
                PolicyResult.success(written)
            }
        } catch (e: SecurityException) {
            PolicyResult.failure(
                status = PolicyStatus.SECURITY_EXCEPTION,
                message = e.message ?: "PackageInstaller staging denied",
                errorType = e.javaClass.name
            )
        } catch (e: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = e.message ?: "PackageInstaller staging failed",
                errorType = e.javaClass.name
            )
        }
    }

    fun stageApkSet(sessionId: Int, files: List<File>): PolicyResult<Long> {
        if (files.isEmpty()) {
            return PolicyResult.failure(PolicyStatus.FAILED, "APK set is empty")
        }
        return try {
            installer.openSession(sessionId).use { session ->
                var total = 0L
                val usedNames = linkedSetOf<String>()
                files.forEachIndexed { index, file ->
                    require(file.isFile) { "APK file missing: ${file.name}" }
                    val preferred = file.name.takeIf { it.endsWith(".apk", ignoreCase = true) }
                        ?: if (index == 0) "base.apk" else "split-$index.apk"
                    val sessionName = if (preferred !in usedNames) preferred else "split-$index.apk"
                    usedNames += sessionName
                    file.inputStream().use { input ->
                        session.openWrite(sessionName, 0, file.length()).use { output ->
                            total += input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
                PolicyResult.success(total)
            }
        } catch (e: SecurityException) {
            PolicyResult.failure(
                status = PolicyStatus.SECURITY_EXCEPTION,
                message = e.message ?: "PackageInstaller split staging denied",
                errorType = e.javaClass.name
            )
        } catch (e: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = e.message ?: "PackageInstaller split staging failed",
                errorType = e.javaClass.name
            )
        }
    }

    fun commit(sessionId: Int, statusReceiver: IntentSender): PolicyResult<Unit> {
        return try {
            installer.openSession(sessionId).use { session ->
                session.commit(statusReceiver)
            }
            PolicyResult.success(message = "Install session commit submitted")
        } catch (e: SecurityException) {
            PolicyResult.failure(
                status = PolicyStatus.SECURITY_EXCEPTION,
                message = e.message ?: "PackageInstaller commit denied",
                errorType = e.javaClass.name
            )
        } catch (e: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = e.message ?: "PackageInstaller commit failed",
                errorType = e.javaClass.name
            )
        }
    }

    fun abandon(sessionId: Int): PolicyResult<Unit> {
        return try {
            installer.abandonSession(sessionId)
            PolicyResult.success()
        } catch (e: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = e.message ?: "PackageInstaller abandon failed",
                errorType = e.javaClass.name
            )
        }
    }

    private fun PackageSource.toPlatformSource(): Int = when (this) {
        PackageSource.STORE -> PackageInstaller.PACKAGE_SOURCE_STORE
        PackageSource.LOCAL_FILE -> PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
        PackageSource.DOWNLOADED_FILE -> PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        PackageSource.OTHER -> PackageInstaller.PACKAGE_SOURCE_OTHER
        PackageSource.UNSPECIFIED -> PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
    }

    private fun InstallPermissionState.toPlatformState(): Int = when (this) {
        InstallPermissionState.DEFAULT -> PackageInstaller.SessionParams.PERMISSION_STATE_DEFAULT
        InstallPermissionState.DENIED -> PackageInstaller.SessionParams.PERMISSION_STATE_DENIED
        InstallPermissionState.GRANTED -> PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED
    }
}
