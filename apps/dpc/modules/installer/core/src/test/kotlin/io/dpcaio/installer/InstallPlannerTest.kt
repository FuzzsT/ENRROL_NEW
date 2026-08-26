package io.dpcaio.installer

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val planner = InstallPlanner()

    val play = planner.plan(
        InstallRequest(packageName = "com.example", preference = InstallPreference.PLAY_COMPAT, requireRealPlay = true),
        InstallAvailability(managedPlay = true, dpcPackageInstaller = true)
    )
    assertEquals(InstallRoute.MANAGED_PLAY, play.selected, "real Play-compatible route should win when explicitly required")

    val local = planner.plan(
        InstallRequest(packageName = "com.example", preference = InstallPreference.DEFAULT),
        InstallAvailability(dpcPackageInstaller = true, shizuku = true, userConfirmation = true)
    )
    assertEquals(InstallRoute.DPC_PACKAGE_INSTALLER, local.selected, "local managed install should prefer DPC PackageInstaller")

    val recordFallback = planner.plan(
        InstallRequest(
            packageName = "com.example",
            preference = InstallPreference.PLAY_COMPAT,
            requireRealPlay = false,
            allowInstallerRecordFallback = true
        ),
        InstallAvailability(installerRecordCompat = true, userConfirmation = true)
    )
    assertEquals(InstallRoute.INSTALLER_RECORD_COMPAT, recordFallback.selected, "installer-record fallback must remain distinct from genuine Play")
    assertTrue(!recordFallback.realPlayVerified, "installer-record compatibility must not be reported as real Play")

    val blocked = planner.plan(
        InstallRequest(packageName = "com.example", preference = InstallPreference.DEFAULT),
        InstallAvailability()
    )
    assertEquals(null, blocked.selected, "no available executor should produce no selected route")
    assertTrue("NO_INSTALL_ROUTE" in blocked.blockers, "blocked plan should explain missing route")

    println("InstallPlannerTest: PASS")
}
