package io.dpcaio.policy.parity

enum class TestDpcImplementationState {
    NATIVE,
    EXPOSE_BACKEND,
    IMPLEMENT_PUBLIC_API,
    MODERN_EQUIVALENT,
    DEVICE_CAPABILITY_REQUIRED,
    DEPRECATED_UNAVAILABLE,
}

enum class OwnerRequirement {
    NONE,
    DEVICE_OWNER,
    PROFILE_OWNER,
    DEVICE_OR_PROFILE_OWNER,
    COPE,
}

enum class PlatformFeature {
    WIFI,
    TELEPHONY,
    EUICC,
    MANAGED_USERS,
    CAMERA,
    NFC,
}

enum class ParityDestination {
    ACTIVITY_MANAGER,
    DEVICE_LIFECYCLE,
    ENTERPRISE_POLICY_HUB,
    ENTERPRISE_OPERATIONS,
    CREDENTIAL_CENTER,
    PERMISSION_MANAGER,
    NETWORK_CONTROL,
    GOOGLE_ACCOUNT_MANAGER,
    WORK_PROFILE_COPE,
    TESTDPC_DETAIL,
}

enum class ParityInputType {
    TEXT,
    PACKAGE_NAME,
    COMPONENT_NAME,
    INTEGER,
    LONG,
    BOOLEAN,
    CSV,
    URI,
}

data class ParityInputField(
    val key: String,
    val label: String,
    val type: ParityInputType,
    val required: Boolean = true,
)

data class TestDpcParityEntry(
    val id: String,
    val testDpcKey: String,
    val googleTitle: String,
    val category: String,
    val description: String = "",
    val implementationState: TestDpcImplementationState,
    val handlerId: String? = null,
    val destination: ParityDestination? = null,
    val minSdk: Int = 21,
    val ownerRequirement: OwnerRequirement = OwnerRequirement.NONE,
    val requiredFeatures: Set<PlatformFeature> = emptySet(),
    val requiredDelegatedScopes: Set<String> = emptySet(),
    val destructive: Boolean = false,
    val deprecated: Boolean = false,
    val unavailableReason: String? = null,
    val replacementGuidance: String? = null,
    val inputs: List<ParityInputField> = emptyList(),
)
