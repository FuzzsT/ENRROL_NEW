package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.installer.InstallerBackendAvailability
import io.dpcaio.installer.InstallerBackendKind
import io.dpcaio.installer.InstallerBackendState
import io.dpcaio.installer.InstallerDoctorSnapshot
import io.dpcaio.shizuku.AndroidShizukuRuntime

class InstallerDoctor(private val context: Context) {
    fun inspect(): InstallerDoctorSnapshot {
        val packageManager = context.packageManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val shizuku = AndroidShizukuRuntime().probe()
        val canRequestPackageInstalls = if (Build.VERSION.SDK_INT >= 26) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
        val packageInstallerAvailable = runCatching { packageManager.packageInstaller != null }.getOrDefault(false)
        val deviceOwner = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        val profileOwner = runCatching { dpm.isProfileOwnerApp(context.packageName) }.getOrDefault(false)

        val packageInstallerState = when {
            !packageInstallerAvailable -> InstallerBackendAvailability.UNAVAILABLE
            canRequestPackageInstalls || deviceOwner || profileOwner -> InstallerBackendAvailability.READY
            else -> InstallerBackendAvailability.NEEDS_USER_ACTION
        }
        val shizukuState = when {
            shizuku.binderAlive && shizuku.permissionGranted -> InstallerBackendAvailability.DIAGNOSTIC_ONLY
            else -> InstallerBackendAvailability.UNAVAILABLE
        }

        return InstallerDoctorSnapshot(
            packageInstallerAvailable = packageInstallerAvailable,
            canRequestPackageInstalls = canRequestPackageInstalls,
            deviceOwner = deviceOwner,
            profileOwner = profileOwner,
            shizukuBinderAlive = shizuku.binderAlive,
            shizukuPermissionGranted = shizuku.permissionGranted,
            backends = listOf(
                InstallerBackendState(
                    InstallerBackendKind.PACKAGE_INSTALLER,
                    packageInstallerState,
                    if (packageInstallerState == InstallerBackendAvailability.READY) "Android PackageInstaller ready" else "Android install permission or managed-device context may be required",
                ),
                InstallerBackendState(
                    InstallerBackendKind.DEVICE_OWNER_CONTEXT,
                    if (deviceOwner || profileOwner) InstallerBackendAvailability.READY else InstallerBackendAvailability.UNAVAILABLE,
                    "deviceOwner=$deviceOwner profileOwner=$profileOwner",
                ),
                InstallerBackendState(
                    InstallerBackendKind.SHIZUKU_DIAGNOSTIC,
                    shizukuState,
                    "binderAlive=${shizuku.binderAlive} permissionGranted=${shizuku.permissionGranted}; DIAGNOSTIC_ONLY",
                ),
            ),
        )
    }
}
