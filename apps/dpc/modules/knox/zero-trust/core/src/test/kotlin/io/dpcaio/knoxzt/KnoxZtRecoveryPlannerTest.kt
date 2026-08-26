package io.dpcaio.knoxzt

private fun expect(actual: Any?, expected: Any?) {
    check(actual == expected) { "expected=$expected actual=$actual" }
}

fun main() {
    val planner = KnoxZtRecoveryPlanner()

    expect(
        planner.plan(KnoxZtProbe(installedForUser = true, enabled = true, systemApp = true, trusted = true), false).selected,
        KnoxZtRecoveryRoute.NONE
    )

    expect(
        planner.plan(KnoxZtProbe(installedForUser = true, enabled = false, systemApp = true, trusted = true), false).selected,
        KnoxZtRecoveryRoute.ENABLE_SYSTEM_APP
    )

    expect(
        planner.plan(KnoxZtProbe(installedForUser = false, knownToSystem = true, enabled = false, systemApp = true, trusted = true), false).routes,
        listOf(KnoxZtRecoveryRoute.INSTALL_EXISTING_PACKAGE, KnoxZtRecoveryRoute.ENABLE_SYSTEM_APP)
    )

    expect(
        planner.plan(KnoxZtProbe(installedForUser = false, knownToSystem = false), true).selected,
        KnoxZtRecoveryRoute.DOWNLOAD_VERIFY_INSTALL
    )

    expect(
        planner.plan(KnoxZtProbe(installedForUser = false, knownToSystem = false), false).blockers,
        listOf("TRUSTED_INSTALL_SOURCE_REQUIRED")
    )

    expect(
        planner.plan(KnoxZtProbe(installedForUser = true, enabled = true, systemApp = false, trusted = false), true).blockers,
        listOf("SIGNATURE_OR_SYSTEM_TRUST_REQUIRED")
    )

    println("KnoxZtRecoveryPlannerTest: PASS")
}
