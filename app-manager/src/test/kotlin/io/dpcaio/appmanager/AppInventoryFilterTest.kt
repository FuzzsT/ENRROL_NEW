package io.dpcaio.appmanager

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val apps = listOf(
        ManagedAppRecord("sys", "System", "1", 1000, 0, systemApp = true, enabled = true, hidden = false, suspended = false),
        ManagedAppRecord("user", "User", "2", 10100, 0, systemApp = false, enabled = true, hidden = false, suspended = false),
        ManagedAppRecord("hidden", "Hidden", "3", 10101, 0, systemApp = false, enabled = true, hidden = true, suspended = false),
        ManagedAppRecord("suspended", "Suspended", "4", 10102, 0, systemApp = false, enabled = true, hidden = false, suspended = true),
        ManagedAppRecord("disabled", "Disabled", "5", 10103, 0, systemApp = false, enabled = false, hidden = false, suspended = false)
    )
    val filter = AppInventoryFilterEngine()
    assertEquals(listOf("sys"), filter.apply(apps, AppInventoryFilter.SYSTEM).map { it.packageName }, "system filter")
    assertEquals(setOf("user", "hidden", "suspended", "disabled"), filter.apply(apps, AppInventoryFilter.USER).map { it.packageName }.toSet(), "user filter")
    assertEquals(listOf("hidden"), filter.apply(apps, AppInventoryFilter.HIDDEN).map { it.packageName }, "hidden filter")
    assertEquals(listOf("suspended"), filter.apply(apps, AppInventoryFilter.SUSPENDED).map { it.packageName }, "suspended filter")
    assertEquals(listOf("disabled"), filter.apply(apps, AppInventoryFilter.DISABLED).map { it.packageName }, "disabled filter")
    println("AppInventoryFilterTest: PASS")
}
