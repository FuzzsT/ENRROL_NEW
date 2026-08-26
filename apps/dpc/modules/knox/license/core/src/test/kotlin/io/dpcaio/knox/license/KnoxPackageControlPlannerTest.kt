package io.dpcaio.knox.license

fun main() {
    val planner = KnoxPackageControlPlanner()

    check(
        planner.plan(
            KnoxPackageControlInput(
                dpmHideAvailable = true,
                dpmSuspendAvailable = true,
                realKnoxActive = false,
                knoxAppMgmtPermission = false,
                labMdmGateActive = true
            )
        ) == KnoxPackageControlRoute.DPM_HIDE
    )

    check(
        planner.plan(
            KnoxPackageControlInput(
                dpmHideAvailable = false,
                dpmSuspendAvailable = true,
                realKnoxActive = false,
                knoxAppMgmtPermission = false,
                labMdmGateActive = true
            )
        ) == KnoxPackageControlRoute.DPM_SUSPEND
    )

    check(
        planner.plan(
            KnoxPackageControlInput(
                dpmHideAvailable = false,
                dpmSuspendAvailable = false,
                realKnoxActive = true,
                knoxAppMgmtPermission = true,
                labMdmGateActive = false
            )
        ) == KnoxPackageControlRoute.REAL_KNOX_APP_MGMT
    )

    check(
        planner.plan(
            KnoxPackageControlInput(
                dpmHideAvailable = false,
                dpmSuspendAvailable = false,
                realKnoxActive = false,
                knoxAppMgmtPermission = false,
                labMdmGateActive = true
            )
        ) == KnoxPackageControlRoute.REAL_KNOX_REQUIRED
    )

    println("KnoxPackageControlPlannerTest: PASS")
}
