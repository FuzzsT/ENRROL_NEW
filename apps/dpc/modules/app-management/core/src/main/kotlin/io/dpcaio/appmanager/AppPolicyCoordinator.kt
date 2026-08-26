package io.dpcaio.appmanager

import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.protection.Mutation
import io.dpcaio.protection.ProtectionDecision
import io.dpcaio.protection.ProtectionPlanner
import io.dpcaio.protection.ProtectionRequest
import io.dpcaio.protection.ProtectedTargetRegistry

enum class AppPolicyVerification {
    VERIFIED,
    MISMATCH,
    FAILED
}

data class AppPolicyOutcome(
    val packageName: String,
    val requestedState: Boolean,
    val observedState: Boolean?,
    val verification: AppPolicyVerification,
    val mutationStatus: PolicyStatus,
    val message: String? = null,
    val protectionDecision: ProtectionDecision = ProtectionDecision.ALLOW,
)

class AppPolicyCoordinator(
    private val gateway: DevicePolicyGateway,
    private val protectionPlanner: ProtectionPlanner = ProtectionPlanner(ProtectedTargetRegistry.default()),
) {
    fun setHidden(packageName: String, hidden: Boolean): AppPolicyOutcome {
        val protectionDecision = protect(packageName, if (hidden) Mutation.HIDE else Mutation.REVERSIBLE)
        if (protectionDecision.blocked()) return blocked(packageName, hidden, protectionDecision)
        val mutation = gateway.setApplicationHidden(packageName, hidden)
        if (!mutation.isSuccess) return failure(packageName, hidden, mutation, protectionDecision)
        val readback = gateway.isApplicationHidden(packageName)
        return verify(packageName, hidden, mutation, readback, protectionDecision)
    }

    fun setSuspended(packageName: String, suspended: Boolean): AppPolicyOutcome {
        val protectionDecision = protect(packageName, if (suspended) Mutation.SUSPEND else Mutation.REVERSIBLE)
        if (protectionDecision.blocked()) return blocked(packageName, suspended, protectionDecision)
        val mutation = gateway.setPackagesSuspended(setOf(packageName), suspended)
        if (!mutation.isSuccess) return failure(packageName, suspended, mutation, protectionDecision)
        if (packageName in (mutation.value ?: emptySet())) {
            return AppPolicyOutcome(
                packageName = packageName,
                requestedState = suspended,
                observedState = null,
                verification = AppPolicyVerification.FAILED,
                mutationStatus = PolicyStatus.PLATFORM_REJECTED,
                message = "Package was returned in the DevicePolicyManager failure set",
                protectionDecision = protectionDecision,
            )
        }
        val readback = gateway.isPackageSuspended(packageName)
        return verify(packageName, suspended, mutation, readback, protectionDecision)
    }

    private fun protect(packageName: String, mutation: Mutation): ProtectionDecision =
        protectionPlanner.decide(ProtectionRequest(targetId = packageName, mutation = mutation))

    private fun ProtectionDecision.blocked(): Boolean = this != ProtectionDecision.ALLOW && this != ProtectionDecision.ALLOW_WITH_CONFIRMATION

    private fun blocked(packageName: String, requested: Boolean, decision: ProtectionDecision) = AppPolicyOutcome(
        packageName = packageName,
        requestedState = requested,
        observedState = null,
        verification = AppPolicyVerification.FAILED,
        mutationStatus = PolicyStatus.NOT_AUTHORIZED,
        message = decision.name,
        protectionDecision = decision,
    )

    private fun <T> failure(packageName: String, requested: Boolean, mutation: PolicyResult<T>, decision: ProtectionDecision) =
        AppPolicyOutcome(
            packageName = packageName,
            requestedState = requested,
            observedState = null,
            verification = AppPolicyVerification.FAILED,
            mutationStatus = mutation.status,
            message = mutation.message,
            protectionDecision = decision,
        )

    private fun <T> verify(
        packageName: String,
        requested: Boolean,
        mutation: PolicyResult<T>,
        readback: PolicyResult<Boolean>,
        decision: ProtectionDecision,
    ): AppPolicyOutcome {
        if (!readback.isSuccess) {
            return AppPolicyOutcome(
                packageName = packageName,
                requestedState = requested,
                observedState = null,
                verification = AppPolicyVerification.FAILED,
                mutationStatus = readback.status,
                message = readback.message,
                protectionDecision = decision,
            )
        }
        val observed = readback.value
        return AppPolicyOutcome(
            packageName = packageName,
            requestedState = requested,
            observedState = observed,
            verification = if (observed == requested) AppPolicyVerification.VERIFIED else AppPolicyVerification.MISMATCH,
            mutationStatus = mutation.status,
            message = if (observed == requested) null else "Policy readback did not match requested state",
            protectionDecision = decision,
        )
    }
}
