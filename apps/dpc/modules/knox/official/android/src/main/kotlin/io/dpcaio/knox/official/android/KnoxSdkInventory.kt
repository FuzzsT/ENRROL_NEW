package io.dpcaio.knox.official.android

import android.content.Context
import android.os.Build
import io.dpcaio.knox.official.KnoxApiDescriptor
import io.dpcaio.model.CapabilityEvidence
import io.dpcaio.model.CapabilityState
import io.dpcaio.model.EnterpriseCapability
import io.dpcaio.model.EnterpriseRoute

data class KnoxSdkSnapshot(
    val samsungDevice: Boolean,
    val knoxEnvironmentPresent: Boolean,
    val detectedClasses: Set<String>,
    val detail: String,
)

class KnoxSdkInventory(private val context: Context) {
    fun snapshot(descriptors: List<KnoxApiDescriptor>): KnoxSdkSnapshot {
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (!samsung) {
            return KnoxSdkSnapshot(false, false, emptySet(), "UNSUPPORTED_DEVICE")
        }
        val classes = descriptors.map { it.className }.filterTo(linkedSetOf()) { name ->
            runCatching { Class.forName(name, false, context.classLoader) }.isSuccess
        }
        return KnoxSdkSnapshot(true, classes.isNotEmpty(), classes, if (classes.isEmpty()) "KNOX_CLASS_MISSING" else "KNOX_ENVIRONMENT_DETECTED")
    }

    fun discoveryCapability(descriptor: KnoxApiDescriptor, snapshot: KnoxSdkSnapshot): EnterpriseCapability {
        val present = descriptor.className in snapshot.detectedClasses
        return EnterpriseCapability(
            id = descriptor.capabilityId,
            route = EnterpriseRoute.KNOX_OFFICIAL,
            state = when {
                !snapshot.samsungDevice -> CapabilityState.UNSUPPORTED_FIRMWARE
                !present -> CapabilityState.CLASS_MISSING
                else -> CapabilityState.UNVERIFIED_PLATFORM_MAPPING
            },
            evidence = if (present) CapabilityEvidence.CLASS_PRESENT else CapabilityEvidence.DISCOVERED_IN_REFERENCE_APK,
            requiredPermission = descriptor.requiredPermission,
            requiredOwner = descriptor.requiredOwner,
            requiredLicense = descriptor.requiredLicense,
            details = if (present) "CLASS_PRESENT_CALL_NOT_VERIFIED" else snapshot.detail,
        )
    }
}
