package io.dpcaio.activity

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val planner = ActivityAccessPlanner()
    val attempts = mutableListOf<ActivityRoute>()
    val executor = ActivityRouteExecutor { route, _ ->
        attempts += route
        if (route == ActivityRoute.FRAMEWORK_EXPLICIT) {
            ActivityRouteResult(route, success = true)
        } else {
            ActivityRouteResult(route, success = false, detail = "not available at runtime")
        }
    }
    val coordinator = ActivityLaunchCoordinator(planner, executor)
    val result = coordinator.launch(
        ActivityAccessInput(
            packageName = "com.example",
            className = ".MainActivity",
            enabled = true,
            exported = true,
            launcherVisible = true,
            sameUid = false,
            userAccessible = true
        )
    )
    assertEquals(ActivityRoute.FRAMEWORK_EXPLICIT, result.selectedRoute, "coordinator should fall back after launcher runtime failure")
    assertEquals(listOf(ActivityRoute.LAUNCHER_APPS, ActivityRoute.FRAMEWORK_EXPLICIT), attempts, "routes should be attempted in plan order")

    val blocked = coordinator.launch(
        ActivityAccessInput(
            packageName = "com.example",
            className = ".Private",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true
        )
    )
    assertEquals(null, blocked.selectedRoute, "blocked component should not execute a fabricated route")

    println("ActivityLaunchCoordinatorTest: PASS")
}
