package io.dpcaio.permission

import io.dpcaio.core.model.ExecutionRoute
import io.dpcaio.core.model.RouteCategory

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val planner = PermissionActionPlanner()

    val managed = planner.plan(
        PermissionInspection(
            rawPermission = RawPermissionState.DENIED,
            appOpState = AppOpState.DEFAULT,
            dpcManageable = true,
            userActionAvailable = true
        )
    )
    assertEquals(PermissionAction.DPC_GRANT, managed.primary, "DPC-manageable runtime permission should prefer DPC grant")

    val altRoute = VerifiedRoute(
        ExecutionRoute("shizuku", RouteCategory.SHIZUKU, available = true, score = 700),
        verified = true
    )
    val alternative = planner.plan(
        PermissionInspection(
            rawPermission = RawPermissionState.DENIED,
            appOpState = AppOpState.IGNORED,
            dpcManageable = false,
            userActionAvailable = true,
            verifiedAlternative = altRoute
        )
    )
    assertEquals(PermissionAction.USE_VERIFIED_ROUTE, alternative.primary, "verified equivalent route should beat manual user action")

    val user = planner.plan(
        PermissionInspection(
            rawPermission = RawPermissionState.DENIED,
            appOpState = null,
            dpcManageable = false,
            userActionAvailable = true
        )
    )
    assertEquals(PermissionAction.USER_ACTION, user.primary, "user action should be exposed when no automated route exists")

    val grantedButBlocked = planner.plan(
        PermissionInspection(
            rawPermission = RawPermissionState.GRANTED,
            appOpState = AppOpState.IGNORED,
            dpcManageable = false,
            userActionAvailable = false
        )
    )
    assertEquals(PermissionAction.BLOCKED, grantedButBlocked.primary, "AppOp IGNORED must prevent direct green state")

    val direct = planner.plan(
        PermissionInspection(
            rawPermission = RawPermissionState.GRANTED,
            appOpState = AppOpState.ALLOWED,
            dpcManageable = false,
            userActionAvailable = false
        )
    )
    assertEquals(PermissionAction.DIRECT_ALREADY_GRANTED, direct.primary, "real grant and allowed AppOp should be direct")

    println("PermissionActionPlannerTest: PASS")
}
