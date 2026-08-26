package io.dpcaio.activity

private fun assertRouterEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val input = ActivityAccessInput("com.example", "MainActivity", true, true, false, false, true)
    val framework = ActivityRouteExecutor { route, _ -> ActivityRouteResult(route, route == ActivityRoute.FRAMEWORK_EXPLICIT, "framework") }
    val shizuku = ActivityRouteExecutor { route, _ -> ActivityRouteResult(route, route == ActivityRoute.SHIZUKU, "shizuku") }
    val router = ActivityExecutorRouter(
        defaultExecutor = framework,
        routeExecutors = mapOf(ActivityRoute.SHIZUKU to shizuku)
    )

    assertRouterEquals(true, router.execute(ActivityRoute.SHIZUKU, input).success, "Shizuku route must use dedicated executor")
    assertRouterEquals("shizuku", router.execute(ActivityRoute.SHIZUKU, input).detail, "dedicated detail")
    assertRouterEquals(true, router.execute(ActivityRoute.FRAMEWORK_EXPLICIT, input).success, "framework route must use default")
    assertRouterEquals("framework", router.execute(ActivityRoute.FRAMEWORK_EXPLICIT, input).detail, "default detail")
    println("ActivityExecutorRouterTest: PASS")
}
