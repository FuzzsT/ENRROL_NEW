package io.dpcaio.appmanager

enum class PinGuardResult {
    NOT_CONFIGURED,
    INVALID,
    LOCKED_OUT,
    SUCCESS,
    STORE_ERROR,
}

data class PinGuardState(
    val configured: Boolean,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
)

data class PinGuardDecision(
    val result: PinGuardResult,
    val failedAttempts: Int,
    val lockoutUntilMillis: Long,
)

class PinGuardPolicy(
    private val maxAttempts: Int = 5,
    private val lockoutMillis: Long = 30_000L,
) {
    init {
        require(maxAttempts > 0)
        require(lockoutMillis > 0L)
    }

    fun evaluate(
        state: PinGuardState,
        attemptMatches: Boolean?,
        nowMillis: Long,
    ): PinGuardDecision {
        if (!state.configured) {
            return PinGuardDecision(PinGuardResult.NOT_CONFIGURED, 0, 0L)
        }
        if (state.lockoutUntilMillis > nowMillis) {
            return PinGuardDecision(
                PinGuardResult.LOCKED_OUT,
                state.failedAttempts,
                state.lockoutUntilMillis,
            )
        }
        if (attemptMatches == null) {
            return PinGuardDecision(PinGuardResult.INVALID, state.failedAttempts, 0L)
        }
        if (attemptMatches) {
            return PinGuardDecision(PinGuardResult.SUCCESS, 0, 0L)
        }

        val failures = state.failedAttempts + 1
        return if (failures >= maxAttempts) {
            PinGuardDecision(PinGuardResult.LOCKED_OUT, failures, nowMillis + lockoutMillis)
        } else {
            PinGuardDecision(PinGuardResult.INVALID, failures, 0L)
        }
    }
}
