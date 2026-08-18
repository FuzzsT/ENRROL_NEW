package io.dpcaio.core.model

enum class RouteCategory {
    FRAMEWORK,
    DEVICE_OWNER,
    PROFILE_OWNER,
    DELEGATED,
    IDENTITY,
    INTENT,
    BINDER,
    PROVIDER,
    COMPANION,
    SHIZUKU,
    SYSTEM,
    NATIVE,
    LAB
}

data class ExecutionRoute(
    val id: String,
    val category: RouteCategory,
    val available: Boolean,
    val score: Int,
    val releaseEligible: Boolean = true,
    val labOnly: Boolean = false
)

data class ExecutionPlan(
    val request: CapabilityRequest,
    val candidates: List<ExecutionRoute>
) {
    val selected: ExecutionRoute? get() = candidates.firstOrNull()
}
