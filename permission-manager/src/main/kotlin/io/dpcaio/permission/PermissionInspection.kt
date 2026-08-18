package io.dpcaio.permission

enum class AppOpState {
    ALLOWED,
    IGNORED,
    ERRORED,
    DEFAULT,
    FOREGROUND,
    UNKNOWN
}

data class PermissionInspection(
    val rawPermission: RawPermissionState,
    val appOpState: AppOpState?,
    val dpcManageable: Boolean,
    val userActionAvailable: Boolean,
    val verifiedAlternative: VerifiedRoute? = null
)

enum class PermissionAction {
    DIRECT_ALREADY_GRANTED,
    DPC_GRANT,
    USE_VERIFIED_ROUTE,
    USER_ACTION,
    BLOCKED
}

data class PermissionActionPlan(
    val primary: PermissionAction,
    val rawPermission: RawPermissionState,
    val appOpState: AppOpState?,
    val alternativeRouteId: String? = null
)
