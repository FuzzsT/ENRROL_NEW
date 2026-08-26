package io.dpcaio.knox.mock

fun main() {
    val gateway = KnoxMockGateway()
    check(gateway.licenseState(labGateActive = true, realKnoxActive = false) == KnoxMockLicenseState.ACTIVE_LAB)
    check(gateway.licenseState(labGateActive = false, realKnoxActive = false) == KnoxMockLicenseState.INACTIVE)
    check(gateway.route(KnoxMockOperation.PACKAGE_HIDE, dpmEquivalentAvailable = true, realKnoxActive = false) == KnoxMockRoute.DPM_FALLBACK)
    check(gateway.route(KnoxMockOperation.KNOX_ONLY_POLICY, dpmEquivalentAvailable = false, realKnoxActive = false) == KnoxMockRoute.REAL_KNOX_REQUIRED)
    println("KnoxMockTest: PASS")
}
