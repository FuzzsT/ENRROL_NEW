package io.dpcaio.shizuku

import io.dpcaio.activity.ActivityAccessInput
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.ActivityRouteExecutor
import io.dpcaio.activity.ActivityRouteResult

class ShizukuActivityRouteExecutor(
    private val client: ShizukuUserServiceClient,
    private val userId: Int
) : ActivityRouteExecutor {
    override fun execute(route: ActivityRoute, input: ActivityAccessInput): ActivityRouteResult {
        if (route != ActivityRoute.SHIZUKU) {
            return ActivityRouteResult(route, false, "Unsupported route for Shizuku executor")
        }
        val result = client.startActivity(input.packageName, input.className, userId)
            ?: return ActivityRouteResult(route, false, "Shizuku user service not connected")
        return ActivityRouteResult(
            route = route,
            success = result == 0,
            detail = "Shizuku am start exit=$result"
        )
    }
}
