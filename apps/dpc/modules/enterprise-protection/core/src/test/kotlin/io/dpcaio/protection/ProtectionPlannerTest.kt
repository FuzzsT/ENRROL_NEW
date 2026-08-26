package io.dpcaio.protection

private fun assertEquals(expected: Any?, actual: Any?, message: String) { if (expected != actual) error("$message: expected=$expected actual=$actual") }

fun main() {
    val planner = ProtectionPlanner(ProtectedTargetRegistry.default())
    assertEquals(
        ProtectionDecision.BLOCK_PROTECTED_TARGET,
        planner.decide(ProtectionRequest("io.dpcaio.app.AioDeviceAdminReceiver", Mutation.DISABLE)),
        "critical DPC receiver cannot be disabled",
    )
    assertEquals(
        ProtectionDecision.BLOCK_NON_REVERSIBLE_AUTOMATION,
        planner.decide(ProtectionRequest("policy", Mutation.NON_REVERSIBLE, automated = true)),
        "non reversible automation blocked",
    )
    assertEquals(
        ProtectionDecision.ALLOW_WITH_CONFIRMATION,
        planner.decide(ProtectionRequest("user.package", Mutation.HIGH_IMPACT_REVERSIBLE)),
        "high impact mutation needs confirmation",
    )
    println("ProtectionPlannerTest: PASS")
}
