package io.dpcaio.execution

private fun assertEquals(expected: Any?, actual: Any?, message: String) { if (expected != actual) error("$message: expected=$expected actual=$actual") }

fun main() {
    val rollback = EnterpriseRollbackPlanner()
    assertEquals(RollbackDecision.ROLLBACK, rollback.decide(pre="A", written="B", current="B"), "written value may rollback")
    assertEquals(RollbackDecision.ALREADY_RESTORED, rollback.decide(pre="A", written="B", current="A"), "pre value is already restored")
    assertEquals(RollbackDecision.CONFLICT_EXTERNAL_CHANGE, rollback.decide(pre="A", written="B", current="C"), "external change preserved")
    val planner = EnterpriseTransactionPlanner()
    val plan = planner.preview(EnterpriseOperation("op-1", "target", "A", "B"))
    assertEquals(EnterpriseTransactionState.PREVIEWED, plan.state, "preview state")
    assertEquals(plan.planHash, planner.preview(EnterpriseOperation("op-1", "target", "A", "B")).planHash, "stable plan hash")

    var applied = false
    val committed = EnterpriseTransactionEngine().execute(
        plan = plan,
        confirmed = true,
        revalidate = { io.dpcaio.protection.ProtectionDecision.ALLOW },
        apply = { desired -> applied = true; desired },
        readback = { "B" },
        rollback = { pre -> pre },
    )
    assertEquals(true, applied, "allowed transaction must apply")
    assertEquals(EnterpriseTransactionState.COMMITTED, committed.state, "verified readback commits")
    assertEquals("B", committed.readback, "receipt captures readback")

    var blockedApplied = false
    val blocked = EnterpriseTransactionEngine().execute(
        plan = plan,
        confirmed = true,
        revalidate = { io.dpcaio.protection.ProtectionDecision.BLOCK_PROTECTED_TARGET },
        apply = { blockedApplied = true; it },
        readback = { "B" },
        rollback = { it },
    )
    assertEquals(false, blockedApplied, "blocked revalidation must fail before apply")
    assertEquals(EnterpriseTransactionState.FAILED, blocked.state, "blocked transaction fails closed")

    var rollbackCalls = 0
    val rollbackPlan = planner.preview(EnterpriseOperation("op-2", "target", "A", "B"))
    val rolledBack = EnterpriseTransactionEngine().execute(
        plan = rollbackPlan,
        confirmed = true,
        revalidate = { io.dpcaio.protection.ProtectionDecision.ALLOW },
        apply = { "B" },
        readback = { null },
        currentState = { "B" },
        rollback = { pre -> rollbackCalls++; pre },
    )
    assertEquals(1, rollbackCalls, "unverifiable applied state must use CAS rollback when unchanged")
    assertEquals(EnterpriseTransactionState.ROLLED_BACK, rolledBack.state, "successful CAS rollback is reported")

    var conflictRollbackCalls = 0
    val conflict = EnterpriseTransactionEngine().execute(
        plan = rollbackPlan,
        confirmed = true,
        revalidate = { io.dpcaio.protection.ProtectionDecision.ALLOW },
        apply = { "B" },
        readback = { "C" },
        currentState = { "C" },
        rollback = { pre -> conflictRollbackCalls++; pre },
    )
    assertEquals(0, conflictRollbackCalls, "external change must never be overwritten")
    assertEquals(EnterpriseTransactionState.CONFLICT_EXTERNAL_CHANGE, conflict.state, "external change is a stable conflict")

    println("EnterpriseTransactionTest: PASS")
}
