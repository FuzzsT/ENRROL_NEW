package io.dpcaio.execution

class EnterpriseRollbackPlanner {
    fun decide(pre: String?, written: String?, current: String?): RollbackDecision = when {
        current == pre -> RollbackDecision.ALREADY_RESTORED
        current == written -> RollbackDecision.ROLLBACK
        else -> RollbackDecision.CONFLICT_EXTERNAL_CHANGE
    }
}
