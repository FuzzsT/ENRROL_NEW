package io.dpcaio.activity

class ActivityExecutorRouter(
    private val defaultExecutor: ActivityRouteExecutor,
    private val routeExecutors: Map<ActivityRoute, ActivityRouteExecutor>
) : ActivityRouteExecutor {
    override fun execute(route: ActivityRoute, input: ActivityAccessInput): ActivityRouteResult {
        return (routeExecutors[route] ?: defaultExecutor).execute(route, input)
    }
}
