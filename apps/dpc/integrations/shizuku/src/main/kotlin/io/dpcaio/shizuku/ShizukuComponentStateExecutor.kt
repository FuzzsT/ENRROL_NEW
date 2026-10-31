package io.dpcaio.shizuku

import io.dpcaio.activity.ComponentOverrideState

data class ShizukuComponentMutationResult(
    val exitCode: Int?,
    val submitted: Boolean,
    val detail: String,
    val observedState: ComponentOverrideState? = null,
    val verified: Boolean = false,
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
        val raw = client.setComponentEnabledStateVerified(packageName, className, state.name, userId)
            ?: return ShizukuComponentMutationResult(null, false, "SHIZUKU_NO_RESULT")
        val normalized = raw.trim()
        when (normalized) {
            "INVALID_ARGUMENT" -> return ShizukuComponentMutationResult(-2, false, normalized)
            "TIMEOUT" -> return ShizukuComponentMutationResult(-3, false, normalized)
            "EXECUTION_ERROR" -> return ShizukuComponentMutationResult(-4, false, normalized)
        }

        val observed = parseObservedState(normalized)
        val verified = observed == state
        val detail = if (verified) {
            "VERIFIED:${observed.name}"
        } else {
            "READBACK_MISMATCH:requested=${state.name} observed=${observed?.name ?: "UNKNOWN"} raw=$normalized"
        }
        return ShizukuComponentMutationResult(
            exitCode = 0,
            submitted = verified,
            detail = detail,
            observedState = observed,
            verified = verified,
        )
    }

    private fun parseObservedState(raw: String): ComponentOverrideState? {
        val stateText = raw.lineSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { line ->
                val marker = "new state:"
                val index = line.indexOf(marker, ignoreCase = true)
                if (index < 0) null else line.substring(index + marker.length).trim().lowercase()
            }
            ?: return null
        return when (stateText) {
            "enabled" -> ComponentOverrideState.ENABLED
            "disabled", "disabled-user", "disabled-until-used" -> ComponentOverrideState.DISABLED
            "default" -> ComponentOverrideState.DEFAULT
            else -> null
        }
    }
}
