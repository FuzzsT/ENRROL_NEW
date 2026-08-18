package io.dpcaio.execution

import io.dpcaio.core.model.BuildTrack
import io.dpcaio.core.model.CapabilityRequest
import io.dpcaio.core.model.CapabilityType
import io.dpcaio.core.model.ExecutionRoute
import io.dpcaio.core.model.RouteCategory

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val planner = ExecutionPlanner()
    val request = CapabilityRequest(
        id = "open-settings",
        type = CapabilityType.ACTIVITY_START,
        buildTrack = BuildTrack.ENTERPRISE_RELEASE
    )

    val routes = listOf(
        ExecutionRoute("unavailable", RouteCategory.FRAMEWORK, available = false, score = 1000),
        ExecutionRoute("framework", RouteCategory.FRAMEWORK, available = true, score = 900),
        ExecutionRoute("shizuku", RouteCategory.SHIZUKU, available = true, score = 700),
        ExecutionRoute(
            "lab-hook",
            RouteCategory.LAB,
            available = true,
            score = 2000,
            releaseEligible = false,
            labOnly = true
        )
    )

    val releasePlan = planner.plan(request, routes)
    assertEquals(listOf("framework", "shizuku"), releasePlan.candidates.map { it.id },
        "release plan should filter unavailable and lab routes, then sort by score")
    assertEquals("framework", releasePlan.selected?.id, "highest eligible route should be selected")

    val labPlan = planner.plan(request.copy(buildTrack = BuildTrack.LAB_DEBUG), routes)
    assertEquals("lab-hook", labPlan.selected?.id, "lab build should be allowed to select lab route")
    assertTrue(labPlan.candidates.none { !it.available }, "unavailable routes must never be candidates")

    val accountRequest = CapabilityRequest(
        id = "google-account-order",
        type = CapabilityType.ACCOUNT_REORDER,
        buildTrack = BuildTrack.ENTERPRISE_RELEASE
    )
    val accountPlan = planner.plan(accountRequest, listOf(
        ExecutionRoute("profile-owner", RouteCategory.PROFILE_OWNER, available = true, score = 950),
        ExecutionRoute("user-assisted", RouteCategory.INTENT, available = true, score = 800)
    ))
    assertEquals("profile-owner", accountPlan.selected?.id, "account reorder should use the highest verified route")

    println("ExecutionPlannerTest: PASS")
}
