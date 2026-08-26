package io.dpcaio.samsung.settings

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val gateway = object : SettingGateway {
        var current = "old"
        var shizukuWrites = 0
        override fun read(namespace: SettingNamespace, key: String): String? = current
        override fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean {
            return when (route) {
                SettingWriteRoute.PUBLIC_SETTINGS -> { current = "old"; true } // simulated Samsung service reversion
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

    println("SamsungSettingEditCoordinatorTest: PASS")
}
