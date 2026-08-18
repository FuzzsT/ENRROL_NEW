package io.dpcaio.activity

fun interface ActivityRouteExecutor {
    fun execute(route: ActivityRoute, input: ActivityAccessInput): ActivityRouteResult
}

data class ActivityRouteResult(
    val route: ActivityRoute,
    val success: Boolean,
    val detail: String? = null
)

data class ActivityLaunchResult(
    val selectedRoute: ActivityRoute?,
    val attempts: List<ActivityRouteResult>,
    val blockers: List<String>
)

class ActivityLaunchCoordinator(
    private val planner: ActivityAccessPlanner,
    private val executor: ActivityRouteExecutor
) {
    fun launch(input: ActivityAccessInput): ActivityLaunchResult {
        val plan = planner.plan(input)
        val attempts = mutableListOf<ActivityRouteResult>()
        for (route in plan.routes) {
            val result = executor.execute(route, input)
            attempts += result
            if (result.success) {
                return ActivityLaunchResult(route, attempts, plan.blockers)
            }
        }
        return ActivityLaunchResult(null, attempts, plan.blockers)
    }
}
