package io.dpcaio.activity

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val planner = ActivityAccessPlanner()

    val launcher = planner.plan(
        ActivityAccessInput(
            packageName = "com.example",
            className = ".MainActivity",
            enabled = true,
            exported = true,
            launcherVisible = true,
            sameUid = false,
            userAccessible = true
        )
    )
    assertEquals(ActivityRoute.LAUNCHER_APPS, launcher.selected, "launcher-visible component should prefer LauncherApps")

    val exportedHidden = planner.plan(
        ActivityAccessInput(
            packageName = "com.example",
            className = ".SettingsActivity",
            enabled = true,
            exported = true,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true,
            deepLinkAvailable = true
        )
    )
    assertTrue(ActivityRoute.FRAMEWORK_EXPLICIT in exportedHidden.routes, "exported hidden activity should allow explicit route")
    assertTrue(ActivityRoute.DEEP_LINK in exportedHidden.routes, "known deep link should be preserved")

    val sameUid = planner.plan(
        ActivityAccessInput(
            packageName = "com.example.shared",
            className = ".PrivateActivity",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = true,
            userAccessible = true
        )
    )
    assertEquals(ActivityRoute.SAME_UID, sameUid.selected, "existing same UID may access non-exported component")
    assertTrue(ActivityRoute.FRAMEWORK_EXPLICIT !in sameUid.routes, "non-exported component must not expose normal explicit route")

    val companion = planner.plan(
        ActivityAccessInput(
            packageName = "com.example.companion",
            className = ".PrivateActivity",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true,
            companionRelayAvailable = true
        )
    )
    assertEquals(ActivityRoute.COMPANION_RELAY, companion.selected, "private activity should use cooperating target relay when available")
    assertTrue(ActivityRoute.FRAMEWORK_EXPLICIT !in companion.routes, "Device Owner status alone must not make private activity exported")

    val blocked = planner.plan(
        ActivityAccessInput(
            packageName = "com.example.locked",
            className = ".PrivateActivity",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true
        )
    )
    assertEquals(null, blocked.selected, "private component without a valid route should remain blocked")

    val labHook = planner.plan(
        ActivityAccessInput(
            packageName = "io.dpcaio.test",
            className = ".PrivateDebugActivity",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true,
            labBuild = true,
            targetOwnedDebuggable = true,
            labJavaHookAvailable = true,
            labArtHookAvailable = true
        )
    )
    assertTrue(ActivityRoute.LAB_JAVA_HOOK in labHook.routes, "lab Java hook route should be exposed only for owned debuggable target")
    assertTrue(ActivityRoute.LAB_ART_HOOK in labHook.routes, "lab ART hook route should be exposed only for owned debuggable target")

    val productionHook = planner.plan(
        ActivityAccessInput(
            packageName = "io.dpcaio.test",
            className = ".PrivateDebugActivity",
            enabled = true,
            exported = false,
            launcherVisible = false,
            sameUid = false,
            userAccessible = true,
            labBuild = false,
            targetOwnedDebuggable = true,
            labJavaHookAvailable = true,
            labArtHookAvailable = true
        )
    )
    assertTrue(ActivityRoute.LAB_JAVA_HOOK !in productionHook.routes && ActivityRoute.LAB_ART_HOOK !in productionHook.routes, "hook routes must not appear in production")

    println("ActivityAccessPlannerTest: PASS")
}
