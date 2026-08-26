package io.dpcaio.shizuku

import io.dpcaio.activity.ComponentOverrideState

data class ShizukuComponentMutationResult(
    val exitCode: Int?,
    val submitted: Boolean,
    val detail: String
)

class ShizukuComponentStateExecutor(
    private val client: ShizukuUserServiceClient
) {
    fun setComponentEnabledState(
        packageName: String,
        className: String,
        userId: Int,
        state: ComponentOverrideState
    ): ShizukuComponentMutationResult {
        val exit = client.setComponentEnabledState(packageName, className, state.name, userId)
        return ShizukuComponentMutationResult(exit, exit == 0, if (exit == 0) "SUBMITTED" else "SHIZUKU_EXIT:$exit")
    }
}
