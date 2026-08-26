package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import io.dpcaio.core.model.BuildTrack
import io.dpcaio.core.model.ManagementContext
import io.dpcaio.core.model.OwnershipMode

object ManagementContextFactory {
    fun create(context: Context): ManagementContext {
        val app = context.applicationContext
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        val prefs = DpcUiPreferences.read(app)
        val packageName = app.packageName
        val deviceOwner = dpm.isDeviceOwnerApp(packageName)
        val profileOwner = dpm.isProfileOwnerApp(packageName)
        val organizationOwned = if (Build.VERSION.SDK_INT >= 30 && profileOwner) {
            runCatching { dpm.isOrganizationOwnedDeviceWithManagedProfile }.getOrDefault(false)
        } else {
            false
        }
        val affiliated = if (Build.VERSION.SDK_INT >= 28) {
            runCatching { dpm.isAffiliatedUser }.getOrDefault(deviceOwner)
        } else {
            deviceOwner
        }
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val realKnox = runCatching { KnoxRuntimeGate.isRealKnoxActive(app) }.getOrDefault(false)
        val labKnox = runCatching { KnoxRuntimeGate.isLabSimulatedActive(app) }.getOrDefault(false)

        return ManagementContext(
            apiLevel = Build.VERSION.SDK_INT,
            ownership = when {
                deviceOwner -> OwnershipMode.DEVICE_OWNER
                profileOwner -> OwnershipMode.PROFILE_OWNER
                else -> OwnershipMode.NONE
            },
            organizationOwnedProfile = organizationOwned,
            samsungDevice = samsung,
            knoxAvailable = samsung || realKnox || labKnox,
            knoxLicenseActive = realKnox,
            buildTrack = buildTrack(),
            showHidden = prefs.showHidden,
            developerMode = prefs.developerMode,
            showExperimental = prefs.showExperimental,
            affiliatedUser = affiliated,
        )
    }

    private fun buildTrack(): BuildTrack = when (BuildConfig.FLAVOR) {
        "enterprise" -> if (BuildConfig.DEBUG) BuildTrack.ENTERPRISE_DEBUG else BuildTrack.ENTERPRISE_RELEASE
        "systemPrivileged" -> BuildTrack.SYSTEM_PRIVILEGED
        else -> BuildTrack.LAB_DEBUG
    }
}
