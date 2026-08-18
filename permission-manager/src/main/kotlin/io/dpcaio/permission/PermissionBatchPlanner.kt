package io.dpcaio.permission

data class PermissionBatchItem(
    val entry: PermissionCatalogEntry,
    val context: PermissionGrantContext
)

data class PermissionBatchPlan(
    val plans: Map<String, PermissionGrantPlan>
) {
    val blocked: List<String> get() = plans.filterValues { it.primary == PermissionGrantRoute.BLOCKED }.keys.toList()
}

class PermissionBatchPlanner(private val planner: PermissionGrantPlanner = PermissionGrantPlanner()) {
    fun plan(items: List<PermissionBatchItem>): PermissionBatchPlan = PermissionBatchPlan(
        items.associate { it.entry.name to planner.plan(it.context) }
    )
}
