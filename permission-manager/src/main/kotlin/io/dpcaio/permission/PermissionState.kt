package io.dpcaio.permission

import io.dpcaio.core.model.ExecutionRoute

enum class RawPermissionState {
    GRANTED,
    DENIED,
    DEFAULT,
    NOT_APPLICABLE
}

enum class EffectiveCapability {
    GREEN_PERMISSION,
    GREEN_COMPAT,
    GREEN_SHIZUKU,
    GREEN_SYSTEM,
    LAB,
    BLOCKED
}

data class VerifiedRoute(
    val route: ExecutionRoute,
    val verified: Boolean
)

data class PermissionCapabilityState(
    val rawPermission: RawPermissionState,
    val effective: EffectiveCapability,
    val routeId: String? = null
)
