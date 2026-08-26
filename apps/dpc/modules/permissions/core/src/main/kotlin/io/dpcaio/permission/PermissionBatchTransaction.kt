package io.dpcaio.permission

import io.dpcaio.policy.ManagedPermissionState

enum class PermissionBatchItemStatus { PLANNED, VERIFIED, SKIPPED, FAILED, READBACK_MISMATCH }

data class PermissionBatchChange(
    val packageName: String,
    val permission: String,
    val userId: Int,
    val previousState: ManagedPermissionState?,
    val requestedState: ManagedPermissionState,
    val decision: PermissionControlDecision
)

data class PermissionBatchPlannedItem(
    val packageName: String,
    val permission: String,
    val userId: Int,
    val previousState: ManagedPermissionState?,
    val requestedState: ManagedPermissionState,
    val route: PermissionControlRoute,
    val status: PermissionBatchItemStatus,
    val detail: String
)

data class PermissionBatchPlan2(
    val supported: List<PermissionBatchPlannedItem>,
    val skipped: List<PermissionBatchPlannedItem>
)

data class PermissionBatchResult2(val results: List<PermissionBatchPlannedItem>)

class PermissionBatchTransaction {
    fun plan(changes: List<PermissionBatchChange>): PermissionBatchPlan2 {
        val supported = mutableListOf<PermissionBatchPlannedItem>()
        val skipped = mutableListOf<PermissionBatchPlannedItem>()
        for (change in changes) {
            val unavailable = change.decision.route == PermissionControlRoute.UNAVAILABLE ||
                change.decision.capability in setOf(
                    PermissionControlCapability.SENSOR_GRANT_RESTRICTED,
                    PermissionControlCapability.PROVISIONING_SENSOR_OPT_OUT,
                    PermissionControlCapability.UNAVAILABLE
                )
            val item = PermissionBatchPlannedItem(
                change.packageName,
                change.permission,
                change.userId,
                change.previousState,
                change.requestedState,
                change.decision.route,
                if (unavailable) PermissionBatchItemStatus.SKIPPED else PermissionBatchItemStatus.PLANNED,
                change.decision.reason
            )
            if (unavailable) skipped += item else supported += item
        }
        return PermissionBatchPlan2(supported, skipped)
    }

    fun finalize(plan: PermissionBatchPlan2, observedStates: Map<String, ManagedPermissionState?>): PermissionBatchResult2 {
        val completed = plan.supported.map { item ->
            val observed = observedStates[item.permission]
            item.copy(
                status = when {
                    observed == null -> PermissionBatchItemStatus.FAILED
                    observed == item.requestedState -> PermissionBatchItemStatus.VERIFIED
                    else -> PermissionBatchItemStatus.READBACK_MISMATCH
                },
                detail = when {
                    observed == null -> "READBACK_FAILED"
                    observed == item.requestedState -> "VERIFIED"
                    else -> "POLICY_READBACK_MISMATCH:$observed"
                }
            )
        } + plan.skipped
        return PermissionBatchResult2(completed)
    }

    fun restorePlan(changes: List<PermissionBatchChange>): List<PermissionBatchChange> = changes.mapNotNull { change ->
        if (change.decision.route !in setOf(PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC)) return@mapNotNull null
        change.previousState?.let { previous -> change.copy(requestedState = previous) }
    }
}
