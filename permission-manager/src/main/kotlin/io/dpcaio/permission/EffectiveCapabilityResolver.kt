package io.dpcaio.permission

import io.dpcaio.core.model.RouteCategory

class EffectiveCapabilityResolver {
    fun resolve(raw: RawPermissionState, verifiedRoute: VerifiedRoute?): PermissionCapabilityState {
        if (raw == RawPermissionState.GRANTED) {
            return PermissionCapabilityState(raw, EffectiveCapability.GREEN_PERMISSION)
        }

        if (verifiedRoute == null || !verifiedRoute.verified) {
            return PermissionCapabilityState(raw, EffectiveCapability.BLOCKED)
        }

        val route = verifiedRoute.route
        val effective = when {
            route.labOnly || route.category == RouteCategory.LAB -> EffectiveCapability.LAB
            route.category == RouteCategory.SHIZUKU -> EffectiveCapability.GREEN_SHIZUKU
            route.category == RouteCategory.SYSTEM -> EffectiveCapability.GREEN_SYSTEM
            else -> EffectiveCapability.GREEN_COMPAT
        }
        return PermissionCapabilityState(raw, effective, route.id)
    }
}
