package io.dpcaio.knox.license

enum class KnoxBuildTrack {
    ENTERPRISE,
    SYSTEM_PRIVILEGED,
    LAB,
    TST,
    ENG
}

enum class KnoxStartupDecision {
    NOT_SAMSUNG,
    WAIT_FOR_DEVICE_OWNER,
    ALLOW_REAL_KNOX,
    ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK,
    ALLOW_DPM_FALLBACK,
    ACTIVATE_REAL_KNOX
}

data class KnoxStartupInput(
    val isSamsung: Boolean,
    val isDeviceOwner: Boolean,
    val buildTrack: KnoxBuildTrack,
    val realLicenseState: KnoxLicenseState,
    val labTokenValid: Boolean,
    val networkAvailable: Boolean,
    val hasRealKeyConfigured: Boolean
)

class KnoxStartupGate {
    fun evaluate(input: KnoxStartupInput): KnoxStartupDecision = when {
        !input.isSamsung -> KnoxStartupDecision.NOT_SAMSUNG
        !input.isDeviceOwner -> KnoxStartupDecision.WAIT_FOR_DEVICE_OWNER
        input.realLicenseState == KnoxLicenseState.ACTIVE -> KnoxStartupDecision.ALLOW_REAL_KNOX
        input.buildTrack in setOf(KnoxBuildTrack.LAB, KnoxBuildTrack.TST, KnoxBuildTrack.ENG) && input.labTokenValid ->
            KnoxStartupDecision.ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK
        input.hasRealKeyConfigured && input.networkAvailable -> KnoxStartupDecision.ACTIVATE_REAL_KNOX
        else -> KnoxStartupDecision.ALLOW_DPM_FALLBACK
    }
}
