package io.dpcaio.core.model

enum class OwnershipMode {
    NONE,
    PROFILE_OWNER,
    DEVICE_OWNER,
}

enum class CapabilityAvailability {
    AVAILABLE,
    OWNER_REQUIRED,
    PROFILE_OWNER_ONLY,
    DEVICE_OWNER_ONLY,
    ORG_OWNED_PROFILE_REQUIRED,
    API_UNAVAILABLE,
    SAMSUNG_ONLY,
    KNOX_LICENSE_REQUIRED,
    LAB_ONLY,
    EXPERIMENTAL,
    UNSUPPORTED,
    AFFILIATION_REQUIRED,
    DELEGATION_REQUIRED,
    LOGGING_DISABLED,
    WAITING_FOR_CALLBACK,
    BATCH_EXPIRED,
    RATE_LIMITED,
    POLICY_CONFLICT,
    POLICY_VALIDATION_FAILED,
    CERT_ADMIN_REQUIRED,
    CERT_DELEGATION_REQUIRED,
    KEYPAIR_NOT_FOUND,
    KEYPAIR_ALREADY_EXISTS,
    APP_NOT_INSTALLED,
    GRANT_SHARED_UID,
    CERT_INVALID,
    CERT_EXPIRED,
    KEY_IMPORT_FAILED,
    HARDWARE_BACKED_UNAVAILABLE,
    LOCK_TASK_NOT_PERMITTED,
    PACKAGE_NOT_ALLOWLISTED,
    WIPE_POLICY_HIGH_RISK,
    FRP_UNSUPPORTED,
    FRP_OWNER_REQUIRED,
    FRP_ACCOUNT_VALIDATION_REQUIRED,
    APP_DATA_CLEAR_REQUIRES_CONFIRMATION,
    POLICY_READBACK_MISMATCH,
    DEPRECATED_PLATFORM_API,
    PREVIEW_API_UNAVAILABLE,
    ERROR,
}

enum class VisibilityClass {
    NORMAL,
    HIDDEN,
    LAB,
    EXPERIMENTAL,
}

enum class OwnershipRequirement {
    ANY,
    DEVICE_OWNER,
    PROFILE_OWNER,
    DEVICE_OR_PROFILE_OWNER,
    DEVICE_OR_ORG_OWNED_PROFILE,
}

enum class RiskClass {
    LOW,
    MEDIUM,
    HIGH,
}

data class ManagementContext(
    val apiLevel: Int,
    val ownership: OwnershipMode,
    val organizationOwnedProfile: Boolean,
    val samsungDevice: Boolean,
    val knoxAvailable: Boolean,
    val knoxLicenseActive: Boolean,
    val buildTrack: BuildTrack,
    val showHidden: Boolean,
    val developerMode: Boolean,
    val showExperimental: Boolean,
    val affiliatedUser: Boolean = false,
)

data class CapabilityRequirements(
    val minApi: Int = 29,
    val maxApi: Int? = null,
    val ownership: OwnershipRequirement = OwnershipRequirement.ANY,
    val requiresSamsung: Boolean = false,
    val requiresKnox: Boolean = false,
    val requiresKnoxLicense: Boolean = false,
    val requiresAffiliation: Boolean = false,
    val visibility: VisibilityClass = VisibilityClass.NORMAL,
    val risk: RiskClass = RiskClass.LOW,
)

data class CapabilityResolution(
    val availability: CapabilityAvailability,
    val visible: Boolean,
    val executable: Boolean,
    val reason: String? = null,
)
