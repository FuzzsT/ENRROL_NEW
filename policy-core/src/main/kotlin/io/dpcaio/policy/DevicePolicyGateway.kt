package io.dpcaio.policy

enum class ManagedPermissionState {
    DEFAULT,
    DENIED,
    GRANTED
}

interface PackagePolicyGateway {
    fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyResult<Unit>
    fun isApplicationHidden(packageName: String): PolicyResult<Boolean>
    fun setPackagesSuspended(packageNames: Set<String>, suspended: Boolean): PolicyResult<Set<String>>
    fun isPackageSuspended(packageName: String): PolicyResult<Boolean>
}

interface PermissionPolicyGateway {
    fun setPermissionGrantState(
        packageName: String,
        permission: String,
        state: ManagedPermissionState
    ): PolicyResult<Unit>

    fun getPermissionGrantState(packageName: String, permission: String): PolicyResult<ManagedPermissionState>
}

interface DevicePolicyGateway : PackagePolicyGateway, PermissionPolicyGateway
