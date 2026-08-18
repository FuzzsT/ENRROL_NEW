package io.dpcaio.appmanager

import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyResult

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private class FakeGateway : DevicePolicyGateway {
    var hidden = false
    var suspended = false
    var rejectHide = false

    override fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyResult<Unit> {
        if (rejectHide) return PolicyResult.failure(io.dpcaio.policy.PolicyStatus.PLATFORM_REJECTED, "rejected")
        this.hidden = hidden
        return PolicyResult.success()
    }

    override fun isApplicationHidden(packageName: String): PolicyResult<Boolean> = PolicyResult.success(hidden)

    override fun setPackagesSuspended(packageNames: Set<String>, suspended: Boolean): PolicyResult<Set<String>> {
        this.suspended = suspended
        return PolicyResult.success(emptySet())
    }

    override fun isPackageSuspended(packageName: String): PolicyResult<Boolean> = PolicyResult.success(suspended)

    override fun setPermissionGrantState(packageName: String, permission: String, state: ManagedPermissionState): PolicyResult<Unit> = PolicyResult.success()

    override fun getPermissionGrantState(packageName: String, permission: String): PolicyResult<ManagedPermissionState> = PolicyResult.success(ManagedPermissionState.DEFAULT)
}

fun main() {
    val gateway = FakeGateway()
    val coordinator = AppPolicyCoordinator(gateway)

    val hidden = coordinator.setHidden("com.example", true)
    assertEquals(AppPolicyVerification.VERIFIED, hidden.verification, "hide must be read back before VERIFIED")
    assertEquals(true, hidden.observedState, "hide readback")

    val suspended = coordinator.setSuspended("com.example", true)
    assertEquals(AppPolicyVerification.VERIFIED, suspended.verification, "suspend must be read back")
    assertEquals(true, suspended.observedState, "suspend readback")

    gateway.rejectHide = true
    val rejected = coordinator.setHidden("com.example", false)
    assertEquals(AppPolicyVerification.FAILED, rejected.verification, "rejected mutation must not be green")

    println("AppPolicyCoordinatorTest: PASS")
}
