package io.dpcaio.samsung.settings

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    runFallbackRouteVerification()
    runVerifiedRollbackAfterFailedWrite()
    runRollbackFailureIsNotReportedAsReverted()
    println("SamsungSettingEditCoordinatorTest: PASS")
}

private fun runFallbackRouteVerification() {
    val gateway = object : SettingGateway {
        var current = "old"
        var shizukuWrites = 0
        override fun read(namespace: SettingNamespace, key: String): String? = current
        override fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean {
            return when (route) {
                SettingWriteRoute.PUBLIC_SETTINGS -> { current = "old"; true }
                SettingWriteRoute.SHIZUKU_SETTINGS -> { shizukuWrites++; current = value; true }
                else -> false
            }
        }
    }

    val coordinator = SamsungSettingEditCoordinator(gateway)
    val result = coordinator.apply(
        SettingEditRequest(
            namespace = SettingNamespace.SECURE,
            key = "example_key",
            value = "new",
            routes = listOf(SettingWriteRoute.PUBLIC_SETTINGS, SettingWriteRoute.SHIZUKU_SETTINGS)
        )
    )
    assertEquals(SettingEditStatus.VERIFIED, result.status, "fallback route should verify after first route reverts")
    assertEquals(SettingWriteRoute.SHIZUKU_SETTINGS, result.verifiedRoute, "Shizuku route should be recorded")
    assertTrue(result.attempts.first().readBack == "old", "first route must capture reverted read-back")
    assertEquals(1, gateway.shizukuWrites, "Shizuku should be tried exactly once")
}

private fun runVerifiedRollbackAfterFailedWrite() {
    val gateway = object : SettingGateway {
        var current = "old"
        var rollbackWrites = 0

        override fun read(namespace: SettingNamespace, key: String): String? = current

        override fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean {
            return when (value) {
                "new" -> {
                    current = "oem-normalized"
                    true
                }
                "old" -> {
                    rollbackWrites++
                    current = "old"
                    true
                }
                else -> false
            }
        }
    }

    val result = SamsungSettingEditCoordinator(gateway).apply(
        SettingEditRequest(
            namespace = SettingNamespace.GLOBAL,
            key = "rollback_key",
            value = "new",
            routes = listOf(SettingWriteRoute.SHIZUKU_SETTINGS),
            verificationDelaysMs = listOf(0L),
        )
    )

    assertEquals(SettingEditStatus.REVERTED, result.status, "failed verified write should restore the original value")
    assertEquals("old", gateway.current, "rollback must actually restore the original value")
    assertEquals("old", result.after, "result.after should report the verified restored value")
    assertEquals(1, gateway.rollbackWrites, "rollback should be attempted exactly once")
}

private fun runRollbackFailureIsNotReportedAsReverted() {
    val gateway = object : SettingGateway {
        var current = "old"

        override fun read(namespace: SettingNamespace, key: String): String? = current

        override fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean {
            return when (value) {
                "new" -> {
                    current = "wrong"
                    true
                }
                "old" -> false
                else -> false
            }
        }
    }

    val result = SamsungSettingEditCoordinator(gateway).apply(
        SettingEditRequest(
            namespace = SettingNamespace.SYSTEM,
            key = "rollback_failure_key",
            value = "new",
            routes = listOf(SettingWriteRoute.PUBLIC_SETTINGS),
            verificationDelaysMs = listOf(0L),
        )
    )

    assertEquals(SettingEditStatus.UNSUPPORTED, result.status, "failed rollback must not be reported as REVERTED")
    assertEquals("wrong", result.after, "after must expose the value left behind when rollback fails")
}
