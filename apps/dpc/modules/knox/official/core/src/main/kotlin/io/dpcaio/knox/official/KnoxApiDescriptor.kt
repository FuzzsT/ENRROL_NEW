package io.dpcaio.knox.official

import io.dpcaio.model.CapabilityEvidence
import io.dpcaio.model.CapabilityState
import io.dpcaio.model.EnterpriseCapability
import io.dpcaio.model.EnterpriseRoute

data class KnoxApiDescriptor(
    val capabilityId: String,
    val className: String,
    val minimumKnoxApi: Int? = null,
    val maximumTestedKnoxApi: Int? = null,
    val requiredPermission: String? = null,
    val requiredOwner: String? = null,
    val requiredLicense: String? = "KPE/KLM",
    val supportsReadback: Boolean = false,
)

data class KnoxPlatformContext(
    val samsungDevice: Boolean,
    val knoxApi: Int?,
    val ownerSatisfied: Boolean,
    val licenseSatisfied: Boolean,
    val permissionSatisfied: Boolean,
)

object KnoxCapabilityReducer {
    fun reduce(descriptor: KnoxApiDescriptor, context: KnoxPlatformContext, classPresent: Boolean): EnterpriseCapability {
        if (!context.samsungDevice) return unavailable(descriptor, CapabilityState.UNSUPPORTED_FIRMWARE, "UNSUPPORTED_DEVICE")
        if (!classPresent) return unavailable(descriptor, CapabilityState.CLASS_MISSING, "CLASS_MISSING")
        val api = context.knoxApi
        if (descriptor.minimumKnoxApi != null && (api == null || api < descriptor.minimumKnoxApi)) {
            return unavailable(descriptor, CapabilityState.API_LEVEL_UNSUPPORTED, "KNOX_API_TOO_OLD")
        }
        if (descriptor.maximumTestedKnoxApi != null && api != null && api > descriptor.maximumTestedKnoxApi) {
            return unavailable(descriptor, CapabilityState.UNVERIFIED_PLATFORM_MAPPING, "UNVERIFIED_PLATFORM_MAPPING")
        }
        if (!context.ownerSatisfied) return unavailable(descriptor, CapabilityState.OWNER_REQUIRED, "OWNER_REQUIRED")
        if (!context.licenseSatisfied) return unavailable(descriptor, CapabilityState.LICENSE_REQUIRED, "LICENSE_REQUIRED")
        if (!context.permissionSatisfied) return unavailable(descriptor, CapabilityState.PERMISSION_REQUIRED, "PERMISSION_REQUIRED")
        return EnterpriseCapability(
            id = descriptor.capabilityId,
            route = EnterpriseRoute.KNOX_OFFICIAL,
            state = CapabilityState.AVAILABLE,
            evidence = CapabilityEvidence.PERMISSION_SATISFIED,
            requiredPermission = descriptor.requiredPermission,
            requiredOwner = descriptor.requiredOwner,
            requiredLicense = descriptor.requiredLicense,
            details = "PREREQUISITES_SATISFIED_CALL_NOT_YET_VERIFIED",
        )
    }

    private fun unavailable(descriptor: KnoxApiDescriptor, state: CapabilityState, detail: String) = EnterpriseCapability(
        id = descriptor.capabilityId,
        route = EnterpriseRoute.KNOX_OFFICIAL,
        state = state,
        evidence = CapabilityEvidence.DISCOVERED_IN_REFERENCE_APK,
        requiredPermission = descriptor.requiredPermission,
        requiredOwner = descriptor.requiredOwner,
        requiredLicense = descriptor.requiredLicense,
        details = detail,
    )
}
