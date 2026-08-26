package io.dpcaio.execution

import io.dpcaio.protection.ProtectionDecision

/**
 * Pure transaction coordinator shared by Android/Knox/SEM/OEM gateways.
 * Platform-specific callers provide bounded apply/readback/rollback functions;
 * this class enforces the common fail-closed state machine and CAS rollback rule.
 */
class EnterpriseTransactionEngine(
    private val rollbackPlanner: EnterpriseRollbackPlanner = EnterpriseRollbackPlanner(),
) {
    fun execute(
        plan: EnterprisePlan,
        confirmed: Boolean,
        revalidate: () -> ProtectionDecision,
        apply: (desiredState: String?) -> String?,
        readback: () -> String?,
        rollback: (preState: String?) -> String?,
        currentState: () -> String? = readback,
    ): EnterpriseReceipt {
        if (plan.state != EnterpriseTransactionState.PREVIEWED) {
            return receipt(plan, EnterpriseTransactionState.FAILED, null, null)
        }
        if (!isAllowed(plan.operation.protectionDecision, confirmed)) {
            return receipt(plan, EnterpriseTransactionState.FAILED, null, null)
        }

        val revalidated = try {
            revalidate()
        } catch (_: Throwable) {
            return receipt(plan, EnterpriseTransactionState.FAILED, null, null)
        }
        if (!isAllowed(revalidated, confirmed)) {
            return receipt(plan, EnterpriseTransactionState.FAILED, null, null)
        }

        val written = try {
            apply(plan.operation.desiredState)
        } catch (_: Throwable) {
            return receipt(plan, EnterpriseTransactionState.FAILED, null, null)
        }

        val observed = try {
            readback()
        } catch (_: Throwable) {
            null
        }
        if (observed == plan.operation.desiredState) {
            return receipt(plan, EnterpriseTransactionState.COMMITTED, written, observed)
        }

        val current = try {
            currentState()
        } catch (_: Throwable) {
            null
        }
        return when (rollbackPlanner.decide(plan.operation.preState, written, current)) {
            RollbackDecision.ALREADY_RESTORED ->
                receipt(plan, EnterpriseTransactionState.ROLLED_BACK, written, observed)

            RollbackDecision.ROLLBACK -> {
                val restored = try {
                    rollback(plan.operation.preState)
                } catch (_: Throwable) {
                    return receipt(plan, EnterpriseTransactionState.FAILED, written, observed)
                }
                if (restored == plan.operation.preState) {
                    receipt(plan, EnterpriseTransactionState.ROLLED_BACK, written, observed)
                } else {
                    receipt(plan, EnterpriseTransactionState.FAILED, written, observed)
                }
            }

            RollbackDecision.CONFLICT_EXTERNAL_CHANGE ->
                receipt(plan, EnterpriseTransactionState.CONFLICT_EXTERNAL_CHANGE, written, observed)
        }
    }

    private fun isAllowed(decision: ProtectionDecision, confirmed: Boolean): Boolean = when (decision) {
        ProtectionDecision.ALLOW -> true
        ProtectionDecision.ALLOW_WITH_CONFIRMATION -> confirmed
        else -> false
    }

    private fun receipt(
        plan: EnterprisePlan,
        state: EnterpriseTransactionState,
        postState: String?,
        readback: String?,
    ) = EnterpriseReceipt(
        planHash = plan.planHash,
        state = state,
        preState = plan.operation.preState,
        postState = postState,
        readback = readback,
    )
}
