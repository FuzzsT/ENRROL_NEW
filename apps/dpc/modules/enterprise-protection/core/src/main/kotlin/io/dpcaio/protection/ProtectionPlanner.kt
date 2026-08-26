package io.dpcaio.protection

class ProtectionPlanner(
    private val registry: ProtectedTargetRegistry,
) {
    fun decide(request: ProtectionRequest): ProtectionDecision {
        if (request.automated && request.mutation == Mutation.NON_REVERSIBLE) {
            return ProtectionDecision.BLOCK_NON_REVERSIBLE_AUTOMATION
        }
        if (!request.ownerSatisfied) return ProtectionDecision.BLOCK_OWNER_MISMATCH
        if (!request.licenseOrPermissionSatisfied) return ProtectionDecision.BLOCK_LICENSE_OR_PERMISSION

        val targetClass = registry.classify(request.targetId)
        val destructive = request.mutation in setOf(
            Mutation.DISABLE,
            Mutation.HIDE,
            Mutation.SUSPEND,
            Mutation.UNINSTALL,
            Mutation.REVOKE_PERMISSION,
        )
        if (destructive && targetClass in setOf(ProtectedTargetClass.DPC_CRITICAL, ProtectedTargetClass.RECOVERY_CRITICAL)) {
            return ProtectionDecision.BLOCK_PROTECTED_TARGET
        }
        if (request.mutation == Mutation.PROTECTED_MUTATION && !request.recoveryPathAvailable) {
            return ProtectionDecision.BLOCK_REQUIRED_RECOVERY_PATH
        }
        if (request.mutation in setOf(Mutation.HIGH_IMPACT_REVERSIBLE, Mutation.PROTECTED_MUTATION)) {
            return ProtectionDecision.ALLOW_WITH_CONFIRMATION
        }
        return ProtectionDecision.ALLOW
    }
}
