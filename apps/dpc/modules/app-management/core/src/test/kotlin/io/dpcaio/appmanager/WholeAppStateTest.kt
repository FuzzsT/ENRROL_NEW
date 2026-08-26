package io.dpcaio.appmanager

import io.dpcaio.protection.ProtectionDecision

fun main() {
    val planner = WholeAppStatePlanner()
    val own = planner.plan(WholeAppStateRequest("com.example.own", 0, enabled = false, sameUid = true))
    check(own.route == WholeAppStateRoute.OWN_UID)
    val shizuku = planner.plan(WholeAppStateRequest("com.example.foreign", 10, enabled = false, shizukuAuthorized = true))
    check(shizuku.route == WholeAppStateRoute.SHIZUKU)
    val noRoute = planner.plan(WholeAppStateRequest("com.example.foreign", 10, enabled = false))
    check(noRoute.route == WholeAppStateRoute.UNAVAILABLE)
    val dpc = planner.plan(WholeAppStateRequest("io.dpcaio.app", 0, enabled = false, sameUid = true))
    check(dpc.protectionDecision == ProtectionDecision.BLOCK_PROTECTED_TARGET)
    check(!dpc.allowed)
    println("WholeAppStateTest: PASS")
}
