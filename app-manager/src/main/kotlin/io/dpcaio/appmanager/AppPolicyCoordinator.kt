package io.dpcaio.appmanager

import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

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
    val message: String? = null
)

class AppPolicyCoordinator(private val gateway: DevicePolicyGateway) {
    fun setHidden(packageName: String, hidden: Boolean): AppPolicyOutcome {
        val mutation = gateway.setApplicationHidden(packageName, hidden)
        if (!mutation.isSuccess) return failure(packageName, hidden, mutation)
        val readback = gateway.isApplicationHidden(packageName)
        return verify(packageName, hidden, mutation, readback)
    }

    fun setSuspended(packageName: String, suspended: Boolean): AppPolicyOutcome {
        val mutation = gateway.setPackagesSuspended(setOf(packageName), suspended)
        if (!mutation.isSuccess) return failure(packageName, suspended, mutation)
        if (packageName in (mutation.value ?: emptySet())) {
            return AppPolicyOutcome(
                packageName = packageName,
                requestedState = suspended,
                observedState = null,
                verification = AppPolicyVerification.FAILED,
                mutationStatus = PolicyStatus.PLATFORM_REJECTED,
                message = "Package was returned in the DevicePolicyManager failure set"
            )
        }
        val readback = gateway.isPackageSuspended(packageName)
        return verify(packageName, suspended, mutation, readback)
    }

    private fun <T> failure(packageName: String, requested: Boolean, mutation: PolicyResult<T>) =
        AppPolicyOutcome(
            packageName = packageName,
            requestedState = requested,
            observedState = null,
            verification = AppPolicyVerification.FAILED,
            mutationStatus = mutation.status,
            message = mutation.message
        )

    private fun <T> verify(
        packageName: String,
        requested: Boolean,
        mutation: PolicyResult<T>,
        readback: PolicyResult<Boolean>
    ): AppPolicyOutcome {
        if (!readback.isSuccess) {
            return AppPolicyOutcome(
                packageName = packageName,
                requestedState = requested,
                observedState = null,
                verification = AppPolicyVerification.FAILED,
                mutationStatus = readback.status,
                message = readback.message
            )
        }
        val observed = readback.value
        return AppPolicyOutcome(
            packageName = packageName,
            requestedState = requested,
            observedState = observed,
            verification = if (observed == requested) AppPolicyVerification.VERIFIED else AppPolicyVerification.MISMATCH,
            mutationStatus = mutation.status,
            message = if (observed == requested) null else "Policy readback did not match requested state"
        )
    }
}
