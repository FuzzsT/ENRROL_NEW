package io.dpcaio.knox.license

fun main() {
    val gate = KnoxStartupGate()

    check(
        gate.evaluate(
            KnoxStartupInput(
                isSamsung = true,
                isDeviceOwner = true,
                buildTrack = KnoxBuildTrack.LAB,
                realLicenseState = KnoxLicenseState.UNKNOWN,
                labTokenValid = true,
                networkAvailable = false,
                hasRealKeyConfigured = false
            )
        ) == KnoxStartupDecision.ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK
    )

    check(
        gate.evaluate(
            KnoxStartupInput(
                isSamsung = true,
                isDeviceOwner = true,
                buildTrack = KnoxBuildTrack.ENTERPRISE,
                realLicenseState = KnoxLicenseState.ACTIVE,
                labTokenValid = false,
                networkAvailable = false,
                hasRealKeyConfigured = true
            )
        ) == KnoxStartupDecision.ALLOW_REAL_KNOX
    )

    check(
        gate.evaluate(
            KnoxStartupInput(
                isSamsung = true,
                isDeviceOwner = true,
                buildTrack = KnoxBuildTrack.ENTERPRISE,
                realLicenseState = KnoxLicenseState.UNKNOWN,
                labTokenValid = false,
                networkAvailable = false,
                hasRealKeyConfigured = false
            )
        ) == KnoxStartupDecision.ALLOW_DPM_FALLBACK
    )

    check(
        gate.evaluate(
            KnoxStartupInput(
                isSamsung = true,
                isDeviceOwner = true,
                buildTrack = KnoxBuildTrack.ENTERPRISE,
                realLicenseState = KnoxLicenseState.UNKNOWN,
                labTokenValid = false,
                networkAvailable = true,
                hasRealKeyConfigured = true
            )
        ) == KnoxStartupDecision.ACTIVATE_REAL_KNOX
    )

    check(
        gate.evaluate(
            KnoxStartupInput(
                isSamsung = false,
                isDeviceOwner = true,
                buildTrack = KnoxBuildTrack.LAB,
                realLicenseState = KnoxLicenseState.UNKNOWN,
                labTokenValid = true,
                networkAvailable = false,
                hasRealKeyConfigured = false
            )
        ) == KnoxStartupDecision.NOT_SAMSUNG
    )

    println("KnoxStartupGateTest: PASS")
}
