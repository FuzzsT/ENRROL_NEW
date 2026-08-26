package io.dpcaio.permission

enum class PermissionGrantRoute {
    ALREADY_GRANTED,
    DPC_RUNTIME_GRANT,
    KNOX_SPECIAL_ACCESS,
    SHIZUKU_RUNTIME_GRANT,
    SHIZUKU_APPOP,
    SYSTEM_PRIVILEGED,
    USER_SPECIAL_ACCESS,
    ALTERNATE_CAPABILITY,
    LAB_HOOK_SIMULATION,
    BLOCKED
}

data class PermissionGrantContext(
    val entry: PermissionCatalogEntry,
    val alreadyGranted: Boolean,
    val dpcCanGrantRuntime: Boolean,
    val shizukuAvailable: Boolean,
    val samsungKnoxSpecialAvailable: Boolean,
    val systemPrivilegedAvailable: Boolean,
    val userActionAvailable: Boolean,
    val alternateCapabilityRouteAvailable: Boolean
)

data class PermissionGrantPlan(
    val primary: PermissionGrantRoute,
    val routes: List<PermissionGrantRoute>
)

class PermissionGrantPlanner {
    fun plan(context: PermissionGrantContext): PermissionGrantPlan {
        if (context.alreadyGranted) return PermissionGrantPlan(PermissionGrantRoute.ALREADY_GRANTED, listOf(PermissionGrantRoute.ALREADY_GRANTED))
        val routes = mutableListOf<PermissionGrantRoute>()
        val p = context.entry.protection

        if (p == PermissionProtection.DANGEROUS && context.dpcCanGrantRuntime) routes += PermissionGrantRoute.DPC_RUNTIME_GRANT
        if (p == PermissionProtection.SPECIAL_ACCESS && context.samsungKnoxSpecialAvailable) routes += PermissionGrantRoute.KNOX_SPECIAL_ACCESS
        if (p == PermissionProtection.DANGEROUS && context.shizukuAvailable) routes += PermissionGrantRoute.SHIZUKU_RUNTIME_GRANT
        if ((p == PermissionProtection.APPOP || p == PermissionProtection.SPECIAL_ACCESS) && context.shizukuAvailable) routes += PermissionGrantRoute.SHIZUKU_APPOP
        if ((p == PermissionProtection.SIGNATURE || p == PermissionProtection.PRIVILEGED || p == PermissionProtection.SIGNATURE_PRIVILEGED || p == PermissionProtection.INTERNAL) && context.systemPrivilegedAvailable) {
            routes += PermissionGrantRoute.SYSTEM_PRIVILEGED
        }
        if (context.userActionAvailable) routes += PermissionGrantRoute.USER_SPECIAL_ACCESS
        if (context.alternateCapabilityRouteAvailable) routes += PermissionGrantRoute.ALTERNATE_CAPABILITY
        if (routes.isEmpty()) routes += PermissionGrantRoute.BLOCKED
        return PermissionGrantPlan(routes.first(), routes.distinct())
    }
}
