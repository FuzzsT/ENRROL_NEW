package io.dpcaio.activity

import io.dpcaio.protection.Mutation
import io.dpcaio.protection.ProtectionDecision
import io.dpcaio.protection.ProtectionPlanner
import io.dpcaio.protection.ProtectionRequest
import io.dpcaio.protection.ProtectedTargetRegistry

enum class ComponentOverrideState { DEFAULT, ENABLED, DISABLED }
enum class ComponentControlRoute { OWN_UID, SHIZUKU, SYSTEM_PRIVILEGED, UNAVAILABLE }
enum class ComponentRisk { LOW, MEDIUM, HIGH, CRITICAL }
enum class ComponentControlStatus {
    COMPONENT_CONTROL_AVAILABLE,
    COMPONENT_CONTROL_UNAVAILABLE,
    OWN_UID_COMPONENT,
    SHIZUKU_COMPONENT_CONTROL,
    SYSTEM_COMPONENT_CONTROL,
    TARGET_USER_UNAVAILABLE,
    COMPONENT_NOT_FOUND,
    PROTECTED_DPC_COMPONENT,
    CRITICAL_SYSTEM_COMPONENT,
    COMPONENT_STATE_MISMATCH,
    BATCH_NOT_ATOMIC
}

object ComponentStateResolver {
    fun effective(manifestEnabled: Boolean, overrideState: ComponentOverrideState): Boolean = when (overrideState) {
        ComponentOverrideState.DEFAULT -> manifestEnabled
        ComponentOverrideState.ENABLED -> true
        ComponentOverrideState.DISABLED -> false
    }
}

data class ComponentControlRequest(
    val packageName: String,
    val className: String,
    val targetUserId: Int,
    val sameUid: Boolean,
    val shizukuAvailable: Boolean,
    val systemPrivilegedAvailable: Boolean,
    val criticalSystemComponent: Boolean,
    val developerLab: Boolean,
    val desiredState: ComponentOverrideState,
    val automated: Boolean = false,
)

data class ComponentControlDecision(
    val packageName: String,
    val className: String,
    val targetUserId: Int,
    val desiredState: ComponentOverrideState,
    val route: ComponentControlRoute,
    val allowed: Boolean,
    val status: ComponentControlStatus,
    val risk: ComponentRisk,
    val detail: String,
    val protectionDecision: ProtectionDecision = ProtectionDecision.ALLOW,
)

class ComponentControlRouter(
    private val protectionPlanner: ProtectionPlanner = ProtectionPlanner(ProtectedTargetRegistry.default()),
) {
    fun resolve(request: ComponentControlRequest): ComponentControlDecision {
        val mutation = when (request.desiredState) {
            ComponentOverrideState.DISABLED -> Mutation.DISABLE
            ComponentOverrideState.ENABLED -> Mutation.ENABLE
            ComponentOverrideState.DEFAULT -> Mutation.REVERSIBLE
        }
        val protectionDecision = protectionPlanner.decide(
            ProtectionRequest(
                targetId = request.className,
                mutation = mutation,
                automated = request.automated,
            )
        )
        if (protectionDecision != ProtectionDecision.ALLOW && protectionDecision != ProtectionDecision.ALLOW_WITH_CONFIRMATION) {
            return decision(
                request,
                ComponentControlRoute.UNAVAILABLE,
                false,
                ComponentControlStatus.PROTECTED_DPC_COMPONENT,
                ComponentRisk.CRITICAL,
                protectionDecision.name,
                protectionDecision,
            )
        }
        if (request.criticalSystemComponent && request.desiredState == ComponentOverrideState.DISABLED) {
            return decision(request, ComponentControlRoute.UNAVAILABLE, false, ComponentControlStatus.CRITICAL_SYSTEM_COMPONENT, ComponentRisk.CRITICAL, "CRITICAL_SYSTEM_COMPONENT", protectionDecision)
        }
        if (request.sameUid) {
            return decision(request, ComponentControlRoute.OWN_UID, true, ComponentControlStatus.OWN_UID_COMPONENT, ComponentRisk.LOW, "OWN_UID_COMPONENT", protectionDecision)
        }
        if (request.shizukuAvailable) {
            return decision(request, ComponentControlRoute.SHIZUKU, true, ComponentControlStatus.SHIZUKU_COMPONENT_CONTROL, if (request.criticalSystemComponent) ComponentRisk.CRITICAL else ComponentRisk.MEDIUM, "SHIZUKU_COMPONENT_CONTROL", protectionDecision)
        }
        if (request.systemPrivilegedAvailable) {
            return decision(request, ComponentControlRoute.SYSTEM_PRIVILEGED, true, ComponentControlStatus.SYSTEM_COMPONENT_CONTROL, if (request.criticalSystemComponent) ComponentRisk.CRITICAL else ComponentRisk.HIGH, "SYSTEM_COMPONENT_CONTROL", protectionDecision)
        }
        return decision(request, ComponentControlRoute.UNAVAILABLE, false, ComponentControlStatus.COMPONENT_CONTROL_UNAVAILABLE, ComponentRisk.MEDIUM, "COMPONENT_CONTROL_UNAVAILABLE", protectionDecision)
    }

    private fun decision(
        r: ComponentControlRequest,
        route: ComponentControlRoute,
        allowed: Boolean,
        status: ComponentControlStatus,
        risk: ComponentRisk,
        detail: String,
        protectionDecision: ProtectionDecision,
    ) = ComponentControlDecision(r.packageName, r.className, r.targetUserId, r.desiredState, route, allowed, status, risk, detail, protectionDecision)
}

data class ComponentBatchPlan(
    val items: List<ComponentControlDecision>,
    val atomic: Boolean,
    val status: ComponentControlStatus
)

class ComponentBatchPlanner {
    fun plan(apiLevel: Int, items: List<ComponentControlDecision>): ComponentBatchPlan {
        val packages = items.map { it.packageName }.toSet()
        val users = items.map { it.targetUserId }.toSet()
        val atomic = apiLevel >= 33 && items.isNotEmpty() && items.all { it.allowed && it.route == ComponentControlRoute.OWN_UID } && packages.size == 1 && users.size == 1
        return ComponentBatchPlan(items, atomic, if (atomic) ComponentControlStatus.COMPONENT_CONTROL_AVAILABLE else ComponentControlStatus.BATCH_NOT_ATOMIC)
    }
}
