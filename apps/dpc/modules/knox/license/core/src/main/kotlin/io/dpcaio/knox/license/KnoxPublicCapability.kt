package io.dpcaio.knox.license

enum class KnoxPublicLicenseState {
    NOT_CONFIGURED,
    ACTIVATING,
    ACTIVE,
    VALIDATING,
    EXPIRED,
    DEACTIVATED,
    QUANTITY_EXHAUSTED,
    SERVER_ERROR,
    USER_CONSENT_REQUIRED,
    UNSUPPORTED,
}

enum class KnoxPublicCapabilityState {
    AVAILABLE,
    LICENSE_REQUIRED,
    UNSUPPORTED,
    DEPRECATED_PLATFORM_API,
}

enum class KnoxPublicCapability(
    val defaultState: KnoxPublicCapabilityState,
    val executable: Boolean,
) {
    APPLICATION_POLICY(KnoxPublicCapabilityState.AVAILABLE, true),
    CERTIFICATE_POLICY(KnoxPublicCapabilityState.AVAILABLE, true),
    KIOSK_MODE(KnoxPublicCapabilityState.AVAILABLE, true),
    FIREWALL_VPN(KnoxPublicCapabilityState.AVAILABLE, true),
    ENHANCED_ATTESTATION(KnoxPublicCapabilityState.AVAILABLE, true),
    KNOX_AUDIT_LOG(KnoxPublicCapabilityState.DEPRECATED_PLATFORM_API, false),
}
