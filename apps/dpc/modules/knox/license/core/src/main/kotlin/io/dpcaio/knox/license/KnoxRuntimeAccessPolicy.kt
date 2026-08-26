package io.dpcaio.knox.license

data class KnoxRuntimeAccess(
    val mdmGateActive: Boolean,
    val realKnoxActive: Boolean,
    val labSimulatedActive: Boolean,
    val allowDpmPackageControl: Boolean,
    val allowKnoxOnlyApis: Boolean
)

class KnoxRuntimeAccessPolicy {
    fun fromDecision(decision: KnoxStartupDecision): KnoxRuntimeAccess = when (decision) {
        KnoxStartupDecision.ALLOW_REAL_KNOX -> KnoxRuntimeAccess(
            mdmGateActive = true,
            realKnoxActive = true,
            labSimulatedActive = false,
            allowDpmPackageControl = true,
            allowKnoxOnlyApis = true
        )
        KnoxStartupDecision.ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK -> KnoxRuntimeAccess(
            mdmGateActive = true,
            realKnoxActive = false,
            labSimulatedActive = true,
            allowDpmPackageControl = true,
            allowKnoxOnlyApis = false
        )
        KnoxStartupDecision.ALLOW_DPM_FALLBACK,
        KnoxStartupDecision.NOT_SAMSUNG -> KnoxRuntimeAccess(
            mdmGateActive = true,
            realKnoxActive = false,
            labSimulatedActive = false,
            allowDpmPackageControl = true,
            allowKnoxOnlyApis = false
        )
        KnoxStartupDecision.ACTIVATE_REAL_KNOX -> KnoxRuntimeAccess(
            mdmGateActive = true,
            realKnoxActive = false,
            labSimulatedActive = false,
            allowDpmPackageControl = true,
            allowKnoxOnlyApis = false
        )
        KnoxStartupDecision.WAIT_FOR_DEVICE_OWNER -> KnoxRuntimeAccess(
            mdmGateActive = true,
            realKnoxActive = false,
            labSimulatedActive = false,
            allowDpmPackageControl = false,
            allowKnoxOnlyApis = false
        )
    }
}
