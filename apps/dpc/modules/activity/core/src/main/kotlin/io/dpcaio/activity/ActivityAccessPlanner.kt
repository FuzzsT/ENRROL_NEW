package io.dpcaio.activity

class ActivityAccessPlanner {
    fun plan(input: ActivityAccessInput): ActivityAccessPlan {
        val blockers = mutableListOf<String>()
        val routes = mutableListOf<Pair<ActivityRoute, Int>>()

        if (!input.enabled) blockers += "ACTIVITY_DISABLED"
        if (!input.userAccessible) blockers += "USER_NOT_ACCESSIBLE"

        val packagePrepared = input.enabled && !input.packageHiddenByDpc && !input.suspendedByDpc
        val frameworkPrepared = packagePrepared && input.userAccessible
        if ((input.packageHiddenByDpc || input.suspendedByDpc) && input.dpcCanPrepare) {
            routes += ActivityRoute.DPC_PREPARE_RETRY to 940
        }

        if (frameworkPrepared) {
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
        } else if (packagePrepared && !input.exported) {
            blockers += "NON_EXPORTED"
        }

        if (packagePrepared && input.shizukuAccessible) routes += ActivityRoute.SHIZUKU to 700
        if (packagePrepared && input.systemPrivilegedAccessible) routes += ActivityRoute.SYSTEM_PRIVILEGED to 650
        if (packagePrepared && input.labBuild && input.targetOwnedDebuggable && input.labJavaHookAvailable) {
            routes += ActivityRoute.LAB_JAVA_HOOK to 150
        }
        if (packagePrepared && input.labBuild && input.targetOwnedDebuggable && input.labArtHookAvailable) {
            routes += ActivityRoute.LAB_ART_HOOK to 140
        }

        return ActivityAccessPlan(
            routes = routes.sortedByDescending { it.second }.map { it.first }.distinct(),
            blockers = blockers.distinct()
        )
    }
}
