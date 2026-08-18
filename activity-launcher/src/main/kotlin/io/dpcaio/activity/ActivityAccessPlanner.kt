package io.dpcaio.activity

class ActivityAccessPlanner {
    fun plan(input: ActivityAccessInput): ActivityAccessPlan {
        val blockers = mutableListOf<String>()
        val routes = mutableListOf<Pair<ActivityRoute, Int>>()

        if (!input.enabled) blockers += "ACTIVITY_DISABLED"
        if (!input.userAccessible) blockers += "USER_NOT_ACCESSIBLE"

        val prepared = input.enabled && input.userAccessible && !input.packageHiddenByDpc && !input.suspendedByDpc
        if ((input.packageHiddenByDpc || input.suspendedByDpc) && input.dpcCanPrepare) {
            routes += ActivityRoute.DPC_PREPARE_RETRY to 940
        }

        if (prepared) {
            if (input.launcherVisible && (input.exported || input.sameUid)) {
                routes += ActivityRoute.LAUNCHER_APPS to 1000
            }
            if (input.exported) {
                routes += ActivityRoute.FRAMEWORK_EXPLICIT to 930
                if (input.deepLinkAvailable) routes += ActivityRoute.DEEP_LINK to 920
            } else {
                blockers += "NON_EXPORTED"
            }
            if (input.sameUid) routes += ActivityRoute.SAME_UID to 910
            if (input.companionRelayAvailable) routes += ActivityRoute.COMPANION_RELAY to 900
            if (input.shizukuAccessible) routes += ActivityRoute.SHIZUKU to 700
            if (input.systemPrivilegedAccessible) routes += ActivityRoute.SYSTEM_PRIVILEGED to 650
            if (input.labBuild && input.targetOwnedDebuggable && input.labJavaHookAvailable) routes += ActivityRoute.LAB_JAVA_HOOK to 150
            if (input.labBuild && input.targetOwnedDebuggable && input.labArtHookAvailable) routes += ActivityRoute.LAB_ART_HOOK to 140
        }

        return ActivityAccessPlan(
            routes = routes.sortedByDescending { it.second }.map { it.first }.distinct(),
            blockers = blockers.distinct()
        )
    }
}
