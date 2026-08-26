package io.dpcaio.knox.license

fun main() {
    val states = KnoxPublicLicenseState.entries.toSet()
    check(KnoxPublicLicenseState.NOT_CONFIGURED in states)
    check(KnoxPublicLicenseState.ACTIVATING in states)
    check(KnoxPublicLicenseState.ACTIVE in states)
    check(KnoxPublicLicenseState.VALIDATING in states)
    check(KnoxPublicLicenseState.EXPIRED in states)
    check(KnoxPublicLicenseState.DEACTIVATED in states)
    check(KnoxPublicLicenseState.QUANTITY_EXHAUSTED in states)
    check(KnoxPublicLicenseState.SERVER_ERROR in states)
    check(KnoxPublicLicenseState.USER_CONSENT_REQUIRED in states)
    check(KnoxPublicLicenseState.UNSUPPORTED in states)

    check(KnoxPublicCapability.KNOX_AUDIT_LOG.defaultState == KnoxPublicCapabilityState.DEPRECATED_PLATFORM_API)
    check(!KnoxPublicCapability.KNOX_AUDIT_LOG.executable)
    check(KnoxPublicCapability.APPLICATION_POLICY.executable)
    println("KnoxPublicCapabilityTest: PASS")
}
