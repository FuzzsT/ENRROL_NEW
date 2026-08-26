package io.dpcaio.protection

enum class ProtectedTargetClass {
    DPC_CRITICAL,
    SYSTEM_CRITICAL,
    OWNER_CRITICAL,
    PROFILE_CRITICAL,
    RECOVERY_CRITICAL,
    SECURITY_CRITICAL,
    OEM_CRITICAL,
    USER_PROTECTED,
}

enum class Mutation {
    READ_ONLY,
    ENABLE,
    DISABLE,
    HIDE,
    SUSPEND,
    UNINSTALL,
    REVOKE_PERMISSION,
    REVERSIBLE,
    HIGH_IMPACT_REVERSIBLE,
    PROTECTED_MUTATION,
    NON_REVERSIBLE,
}

data class ProtectedTarget(
    val id: String,
    val targetClass: ProtectedTargetClass,
)

data class ProtectionRequest(
    val targetId: String,
    val mutation: Mutation,
    val automated: Boolean = false,
    val ownerSatisfied: Boolean = true,
    val licenseOrPermissionSatisfied: Boolean = true,
    val recoveryPathAvailable: Boolean = true,
)

enum class ProtectionDecision {
    ALLOW,
    ALLOW_WITH_CONFIRMATION,
    BLOCK_PROTECTED_TARGET,
    BLOCK_REQUIRED_RECOVERY_PATH,
    BLOCK_OWNER_MISMATCH,
    BLOCK_LICENSE_OR_PERMISSION,
    BLOCK_NON_REVERSIBLE_AUTOMATION,
}
