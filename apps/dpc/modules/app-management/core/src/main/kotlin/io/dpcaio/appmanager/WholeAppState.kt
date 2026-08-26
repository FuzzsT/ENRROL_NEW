package io.dpcaio.appmanager

import io.dpcaio.protection.Mutation
import io.dpcaio.protection.ProtectionDecision
import io.dpcaio.protection.ProtectionPlanner
import io.dpcaio.protection.ProtectionRequest
import io.dpcaio.protection.ProtectedTargetRegistry

enum class WholeAppStateRoute { OWN_UID, SHIZUKU, SYSTEM_PRIVILEGED, UNAVAILABLE }

data class WholeAppStateRequest(
    val packageName: String,
    val targetUserId: Int,
    val enabled: Boolean,
    val sameUid: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val systemPrivilegedAvailable: Boolean = false,
    val automated: Boolean = false,
)

data class WholeAppStatePlan(
    val packageName: String,
    val targetUserId: Int,
    val enabled: Boolean,
    val route: WholeAppStateRoute,
    val allowed: Boolean,
    val protectionDecision: ProtectionDecision,
    val detail: String,
)

class WholeAppStatePlanner(
    private val protectionPlanner: ProtectionPlanner = ProtectionPlanner(ProtectedTargetRegistry.default()),
) {
    fun plan(request: WholeAppStateRequest): WholeAppStatePlan {
        val protection = protectionPlanner.decide(
            ProtectionRequest(
                targetId = request.packageName,
                mutation = if (request.enabled) Mutation.ENABLE else Mutation.DISABLE,
                automated = request.automated,
            )
        )
        if (protection !in setOf(ProtectionDecision.ALLOW, ProtectionDecision.ALLOW_WITH_CONFIRMATION)) {
            return WholeAppStatePlan(request.packageName, request.targetUserId, request.enabled, WholeAppStateRoute.UNAVAILABLE, false, protection, protection.name)
        }
        val route = when {
            request.sameUid -> WholeAppStateRoute.OWN_UID
            request.shizukuAuthorized -> WholeAppStateRoute.SHIZUKU
            request.systemPrivilegedAvailable -> WholeAppStateRoute.SYSTEM_PRIVILEGED
            else -> WholeAppStateRoute.UNAVAILABLE
        }
        return WholeAppStatePlan(
            request.packageName,
            request.targetUserId,
            request.enabled,
            route,
            route != WholeAppStateRoute.UNAVAILABLE,
            protection,
            if (route == WholeAppStateRoute.UNAVAILABLE) "NO_NATURAL_PACKAGE_MANAGER_ROUTE" else route.name,
        )
    }
}
