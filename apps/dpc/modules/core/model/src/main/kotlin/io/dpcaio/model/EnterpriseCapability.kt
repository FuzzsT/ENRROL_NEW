package io.dpcaio.model

enum class EnterpriseRoute {
    ANDROID_DPM,
    KNOX_OFFICIAL,
    SAMSUNG_SEM,
    OEM_INTERNAL,
    UNAVAILABLE,
}

enum class CapabilityState {
    AVAILABLE,
    CLASS_MISSING,
    METHOD_MISSING,
    SERVICE_MISSING,
    PERMISSION_REQUIRED,
    OWNER_REQUIRED,
    LICENSE_REQUIRED,
    UNSUPPORTED_FIRMWARE,
    API_LEVEL_UNSUPPORTED,
    CALL_BLOCKED,
    READ_ONLY,
    UNVERIFIED_PLATFORM_MAPPING,
    UNAVAILABLE,
}

enum class CapabilityEvidence {
    DISCOVERED_IN_REFERENCE_APK,
    CLASS_PRESENT,
    METHOD_PRESENT,
    PERMISSION_SATISFIED,
    OWNER_SATISFIED,
    LICENSE_SATISFIED,
    CALL_SUCCEEDED,
    READBACK_VERIFIED,
}

data class EnterpriseCapability(
    val id: String,
    val route: EnterpriseRoute,
    val state: CapabilityState,
    val evidence: CapabilityEvidence,
    val requiredPermission: String? = null,
    val requiredOwner: String? = null,
    val requiredLicense: String? = null,
    val details: String? = null,
) {
    val operational: Boolean
        get() = state == CapabilityState.AVAILABLE && evidence in setOf(
            CapabilityEvidence.CALL_SUCCEEDED,
            CapabilityEvidence.READBACK_VERIFIED,
        )
}
