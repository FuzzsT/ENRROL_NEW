package io.dpcaio.policy

fun main() {
    val lock = LockTaskPolicySpec(setOf("io.dpcaio.app", "com.example.pos"), featureMask = 3)
    check(lock.packages.size == 2)
    check(lock.valid())
    check(!LockTaskPolicySpec(setOf("bad package"), 0).valid())

    check(DeviceSecurityPolicySpec(maxFailedPasswordsForWipe = 0).wipeRisk == LifecycleRisk.NONE)
    check(DeviceSecurityPolicySpec(maxFailedPasswordsForWipe = 10).wipeRisk == LifecycleRisk.CRITICAL)

    val frp = FrpPolicySpec(enabled = true, accountIds = listOf("acct-1", "acct-2"))
    check(frp.valid())
    check(!FrpPolicySpec(enabled = true, accountIds = listOf(" ")).valid())

    val clear = AppControlRequest("com.example.app", AppControlAction.CLEAR_DATA)
    check(clear.risk == LifecycleRisk.HIGH)

    println("DeviceLifecycleModelsTest: PASS")
}
