package io.dpcaio.permission

import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyStatus

enum class PermissionPolicyVerification {
    VERIFIED,
    MISMATCH,
    FAILED
}

data class PermissionPolicyOutcome(
    val packageName: String,
    val permission: String,
    val requestedState: ManagedPermissionState,
    val observedState: ManagedPermissionState?,
    val verification: PermissionPolicyVerification,
    val status: PolicyStatus,
    val message: String? = null
)

class PermissionPolicyCoordinator(private val gateway: DevicePolicyGateway) {
    fun setState(
        packageName: String,
        permission: String,
        state: ManagedPermissionState
    ): PermissionPolicyOutcome {
        val mutation = gateway.setPermissionGrantState(packageName, permission, state)
        if (!mutation.isSuccess) {
            return PermissionPolicyOutcome(
                packageName = packageName,
                permission = permission,
                requestedState = state,
                observedState = null,
                verification = PermissionPolicyVerification.FAILED,
                status = mutation.status,
                message = mutation.message
            )
        }
        val readback = gateway.getPermissionGrantState(packageName, permission)
        if (!readback.isSuccess) {
            return PermissionPolicyOutcome(
                packageName = packageName,
                permission = permission,
                requestedState = state,
                observedState = null,
                verification = PermissionPolicyVerification.FAILED,
                status = readback.status,
                message = readback.message
            )
        }
        val observed = readback.value
        return PermissionPolicyOutcome(
            packageName = packageName,
            permission = permission,
            requestedState = state,
            observedState = observed,
            verification = if (observed == state) PermissionPolicyVerification.VERIFIED else PermissionPolicyVerification.MISMATCH,
            status = mutation.status,
            message = if (observed == state) null else "Permission readback did not match requested state"
        )
    }
}
