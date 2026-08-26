package io.dpcaio.permission

import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private class FakePermissionGateway : DevicePolicyGateway {
    var permissionState = ManagedPermissionState.DEFAULT
    var reject = false
    override fun setPermissionGrantState(packageName: String, permission: String, state: ManagedPermissionState): PolicyResult<Unit> {
        if (reject) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "rejected")
        permissionState = state
        return PolicyResult.success()
    }
    override fun getPermissionGrantState(packageName: String, permission: String) = PolicyResult.success(permissionState)
    override fun setApplicationHidden(packageName: String, hidden: Boolean) = PolicyResult.success<Unit>()
    override fun isApplicationHidden(packageName: String) = PolicyResult.success(false)
    override fun setPackagesSuspended(packageNames: Set<String>, suspended: Boolean) = PolicyResult.success(emptySet<String>())
    override fun isPackageSuspended(packageName: String) = PolicyResult.success(false)
}

fun main() {
    val gateway = FakePermissionGateway()
    val coordinator = PermissionPolicyCoordinator(gateway)
    val granted = coordinator.setState("com.example", "android.permission.CAMERA", ManagedPermissionState.GRANTED)
    assertEquals(PermissionPolicyVerification.VERIFIED, granted.verification, "permission mutation should require readback")
    assertEquals(ManagedPermissionState.GRANTED, granted.observedState, "grant readback")

    gateway.reject = true
    val rejected = coordinator.setState("com.example", "android.permission.CAMERA", ManagedPermissionState.DENIED)
    assertEquals(PermissionPolicyVerification.FAILED, rejected.verification, "rejected state must not become green")
    println("PermissionPolicyCoordinatorTest: PASS")
}
