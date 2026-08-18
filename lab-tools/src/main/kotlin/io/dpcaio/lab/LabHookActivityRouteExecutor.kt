package io.dpcaio.lab

import io.dpcaio.activity.ActivityAccessInput
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.ActivityRouteExecutor
import io.dpcaio.activity.ActivityRouteResult

class LabHookActivityRouteExecutor(
    private val bridge: LabHookActivityBridge
) : ActivityRouteExecutor {
    override fun execute(route: ActivityRoute, input: ActivityAccessInput): ActivityRouteResult {
        val kind = when (route) {
            ActivityRoute.LAB_JAVA_HOOK -> LabHookKind.JAVA
            ActivityRoute.LAB_ART_HOOK -> LabHookKind.ART
            else -> return ActivityRouteResult(route, false, "NOT_LAB_HOOK_ROUTE")
        }
        val result = bridge.installThenLaunch(kind, input.packageName, input.className)
        return ActivityRouteResult(route, result.hookInstalled && result.launched, result.detail)
    }
}
