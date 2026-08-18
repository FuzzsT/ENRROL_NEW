package io.dpcaio.knox.license

enum class KnoxPackageControlRoute {
    DPM_HIDE,
    DPM_SUSPEND,
    REAL_KNOX_APP_MGMT,
    REAL_KNOX_REQUIRED
}

data class KnoxPackageControlInput(
    val dpmHideAvailable: Boolean,
    val dpmSuspendAvailable: Boolean,
    val realKnoxActive: Boolean,
    val knoxAppMgmtPermission: Boolean,
    val labMdmGateActive: Boolean
)

class KnoxPackageControlPlanner {
    fun plan(input: KnoxPackageControlInput): KnoxPackageControlRoute = when {
        input.dpmHideAvailable -> KnoxPackageControlRoute.DPM_HIDE
        input.dpmSuspendAvailable -> KnoxPackageControlRoute.DPM_SUSPEND
        input.realKnoxActive && input.knoxAppMgmtPermission -> KnoxPackageControlRoute.REAL_KNOX_APP_MGMT
        else -> KnoxPackageControlRoute.REAL_KNOX_REQUIRED
    }
}
