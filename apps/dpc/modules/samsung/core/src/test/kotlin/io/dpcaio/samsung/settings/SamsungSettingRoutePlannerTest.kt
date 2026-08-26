package io.dpcaio.samsung.settings

private fun assertEquals(expected: Any?, actual: Any?, message: String) { if (expected != actual) error("$message: expected=$expected actual=$actual") }
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val planner = SamsungSettingRoutePlanner()
    val secure = planner.plan(SettingRouteContext(SettingNamespace.SECURE, isSamsung = true, publicWriteSettings = true, shizukuAvailable = true, systemPrivileged = true, knoxDeepSettingsAvailable = false))
    assertEquals(SettingWriteRoute.SHIZUKU_SETTINGS, secure.first(), "secure setting should not use public WRITE_SETTINGS")
    assertTrue(SettingWriteRoute.SYSTEM_PRIVILEGED in secure, "secure setting may use actual privileged route")

    val deep = planner.plan(SettingRouteContext(SettingNamespace.KNOX_DEEP_SETTING, isSamsung = true, publicWriteSettings = false, shizukuAvailable = true, systemPrivileged = true, knoxDeepSettingsAvailable = true))
    assertEquals(listOf(SettingWriteRoute.KNOX_DEEP_SETTINGS), deep, "Knox deep settings must be a distinct supported route")

    println("SamsungSettingRoutePlannerTest: PASS")
}
