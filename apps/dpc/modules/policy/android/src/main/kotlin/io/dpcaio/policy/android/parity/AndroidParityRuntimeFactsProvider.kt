package io.dpcaio.policy.android.parity

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.policy.parity.ParityRuntimeFacts
import io.dpcaio.policy.parity.PlatformFeature

class AndroidParityRuntimeFactsProvider(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val packageManager = appContext.packageManager

    fun read(): ParityRuntimeFacts {
        val packageName = appContext.packageName
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val isProfileOwner = dpm.isProfileOwnerApp(packageName)
        val isCope = if (Build.VERSION.SDK_INT >= 30 && isProfileOwner) {
            runCatching { dpm.isOrganizationOwnedDeviceWithManagedProfile }.getOrDefault(false)
        } else {
            false
        }

        return ParityRuntimeFacts(
            sdkInt = Build.VERSION.SDK_INT,
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            isCope = isCope,
            features = buildSet {
                addFeature(PackageManager.FEATURE_WIFI, PlatformFeature.WIFI)
                addFeature(PackageManager.FEATURE_TELEPHONY, PlatformFeature.TELEPHONY)
                addFeature(PackageManager.FEATURE_TELEPHONY_EUICC, PlatformFeature.EUICC)
                addFeature(PackageManager.FEATURE_CAMERA_ANY, PlatformFeature.CAMERA)
                addFeature(PackageManager.FEATURE_NFC, PlatformFeature.NFC)
                addFeature(PackageManager.FEATURE_MANAGED_USERS, PlatformFeature.MANAGED_USERS)
            },
            delegatedScopes = delegatedScopes(packageName),
        )
    }

    private fun MutableSet<PlatformFeature>.addFeature(
        featureName: String,
        feature: PlatformFeature,
    ) {
        if (packageManager.hasSystemFeature(featureName)) {
            add(feature)
        }
    }

    private fun delegatedScopes(packageName: String): Set<String> {
        if (Build.VERSION.SDK_INT < 26) return emptySet()
        return runCatching {
            dpm.getDelegatedScopes(admin, packageName).toSet()
        }.getOrDefault(emptySet())
    }
}
