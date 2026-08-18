package io.dpcaio.knox.license

fun main() {
    val planner = KnoxActivationPlanner()

    check(planner.plan(KnoxActivationInput(false, true, true, false, KnoxLicenseState.UNKNOWN)) == KnoxActivationAction.NOT_SAMSUNG)
    check(planner.plan(KnoxActivationInput(true, false, true, true, KnoxLicenseState.UNKNOWN)) == KnoxActivationAction.WAIT_FOR_DEVICE_OWNER)
    check(planner.plan(KnoxActivationInput(true, true, false, true, KnoxLicenseState.UNKNOWN)) == KnoxActivationAction.NEED_VALID_KEY)
    check(planner.plan(KnoxActivationInput(true, true, true, false, KnoxLicenseState.UNKNOWN)) == KnoxActivationAction.QUEUE_FOR_NETWORK)
    check(planner.plan(KnoxActivationInput(true, true, true, true, KnoxLicenseState.UNKNOWN)) == KnoxActivationAction.ACTIVATE_NOW)
    check(planner.plan(KnoxActivationInput(true, true, true, false, KnoxLicenseState.ACTIVE)) == KnoxActivationAction.KEEP_ACTIVE)

    val interpreter = KnoxLicenseResultInterpreter()
    check(interpreter.fromErrorCode(0) == KnoxLicenseState.ACTIVE)
    check(interpreter.fromErrorCode(501) == KnoxLicenseState.OFFLINE_PENDING)
    check(interpreter.fromErrorCode(502) == KnoxLicenseState.OFFLINE_PENDING)
    check(interpreter.fromErrorCode(206) == KnoxLicenseState.FAILED_BINDING)
    check(interpreter.fromErrorCode(203) == KnoxLicenseState.FAILED_TERMINATED)
    check(interpreter.fromErrorCode(999) == KnoxLicenseState.FAILED)

    println("KnoxActivationPlannerTest: PASS")
}
