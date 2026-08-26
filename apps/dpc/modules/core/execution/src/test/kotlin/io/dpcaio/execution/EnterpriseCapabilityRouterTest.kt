package io.dpcaio.execution

import io.dpcaio.model.*

fun main() {
    val router = EnterpriseCapabilityRouter()
    val routes = listOf(
        EnterpriseCapability("x", EnterpriseRoute.OEM_INTERNAL, CapabilityState.AVAILABLE, CapabilityEvidence.CALL_SUCCEEDED),
        EnterpriseCapability("x", EnterpriseRoute.SAMSUNG_SEM, CapabilityState.AVAILABLE, CapabilityEvidence.CALL_SUCCEEDED),
        EnterpriseCapability("x", EnterpriseRoute.KNOX_OFFICIAL, CapabilityState.AVAILABLE, CapabilityEvidence.CALL_SUCCEEDED),
        EnterpriseCapability("x", EnterpriseRoute.ANDROID_DPM, CapabilityState.AVAILABLE, CapabilityEvidence.READBACK_VERIFIED),
    )
    check(router.route("x", routes).route == EnterpriseRoute.ANDROID_DPM)
    check(router.route("x", routes.dropLast(1)).route == EnterpriseRoute.KNOX_OFFICIAL)
    val unavailable = router.route("missing", emptyList())
    check(unavailable.route == EnterpriseRoute.UNAVAILABLE)
    check(unavailable.state == CapabilityState.UNAVAILABLE)
    println("EnterpriseCapabilityRouterTest: PASS")
}
