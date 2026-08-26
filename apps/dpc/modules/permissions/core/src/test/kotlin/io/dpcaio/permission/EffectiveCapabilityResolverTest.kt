package io.dpcaio.permission

import io.dpcaio.core.model.ExecutionRoute
import io.dpcaio.core.model.RouteCategory

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val resolver = EffectiveCapabilityResolver()

    val direct = resolver.resolve(RawPermissionState.GRANTED, null)
    assertEquals(EffectiveCapability.GREEN_PERMISSION, direct.effective,
        "real grant must remain distinguishable from compatibility routes")

    val shizuku = resolver.resolve(
        RawPermissionState.DENIED,
        VerifiedRoute(ExecutionRoute("shizuku", RouteCategory.SHIZUKU, true, 700), verified = true)
    )
    assertEquals(RawPermissionState.DENIED, shizuku.rawPermission, "raw denial must be preserved")
    assertEquals(EffectiveCapability.GREEN_SHIZUKU, shizuku.effective,
        "verified Shizuku route may make capability green without changing raw grant")

    val compat = resolver.resolve(
        RawPermissionState.DENIED,
        VerifiedRoute(ExecutionRoute("relay", RouteCategory.COMPANION, true, 850), verified = true)
    )
    assertEquals(EffectiveCapability.GREEN_COMPAT, compat.effective,
        "verified equivalent route should be represented as compatibility green")

    val lab = resolver.resolve(
        RawPermissionState.DENIED,
        VerifiedRoute(
            ExecutionRoute("art-test", RouteCategory.LAB, true, 100, releaseEligible = false, labOnly = true),
            verified = true
        )
    )
    assertEquals(EffectiveCapability.LAB, lab.effective,
        "lab route must never be reported as a real permission/capability grant")

    val blocked = resolver.resolve(RawPermissionState.DENIED, null)
    assertEquals(EffectiveCapability.BLOCKED, blocked.effective, "no verified route should remain blocked")

    println("EffectiveCapabilityResolverTest: PASS")
}
