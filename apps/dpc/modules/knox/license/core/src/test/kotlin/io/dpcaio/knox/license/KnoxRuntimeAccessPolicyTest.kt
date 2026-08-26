package io.dpcaio.knox.license

fun main() {
    val policy = KnoxRuntimeAccessPolicy()

    val lab = policy.fromDecision(KnoxStartupDecision.ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK)
    check(lab.mdmGateActive)
    check(lab.labSimulatedActive)
    check(!lab.realKnoxActive)
    check(lab.allowDpmPackageControl)
    check(!lab.allowKnoxOnlyApis)

    val real = policy.fromDecision(KnoxStartupDecision.ALLOW_REAL_KNOX)
    check(real.mdmGateActive)
    check(real.realKnoxActive)
    check(!real.labSimulatedActive)
    check(real.allowDpmPackageControl)
    check(real.allowKnoxOnlyApis)

    val fallback = policy.fromDecision(KnoxStartupDecision.ALLOW_DPM_FALLBACK)
    check(fallback.mdmGateActive)
    check(!fallback.realKnoxActive)
    check(fallback.allowDpmPackageControl)
    check(!fallback.allowKnoxOnlyApis)


    val activating = policy.fromDecision(KnoxStartupDecision.ACTIVATE_REAL_KNOX)
    check(activating.mdmGateActive)
    check(!activating.realKnoxActive)
    check(activating.allowDpmPackageControl)
    check(!activating.allowKnoxOnlyApis)

    val waitingOwner = policy.fromDecision(KnoxStartupDecision.WAIT_FOR_DEVICE_OWNER)
    check(waitingOwner.mdmGateActive)
    check(!waitingOwner.allowDpmPackageControl)
    check(!waitingOwner.allowKnoxOnlyApis)

    println("KnoxRuntimeAccessPolicyTest: PASS")
}
