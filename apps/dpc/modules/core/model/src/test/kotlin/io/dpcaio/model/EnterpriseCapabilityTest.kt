package io.dpcaio.model

fun main() {
    val capability = EnterpriseCapability(
        id = "app.hide",
        route = EnterpriseRoute.ANDROID_DPM,
        state = CapabilityState.AVAILABLE,
        evidence = CapabilityEvidence.READBACK_VERIFIED,
    )
    check(capability.operational)
    check(!capability.copy(evidence = CapabilityEvidence.METHOD_PRESENT).operational)
    check(!capability.copy(state = CapabilityState.UNVERIFIED_PLATFORM_MAPPING).operational)
    println("EnterpriseCapabilityTest: PASS")
}
