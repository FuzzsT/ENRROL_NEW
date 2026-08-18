package io.dpcaio.permission

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val samsung = PermissionCatalogEntry(
        name = "com.sec.enterprise.permission-group.mdm",
        group = null,
        declaringPackage = "com.sec.enterprise",
        protection = PermissionProtection.UNKNOWN,
        publicSdkConstant = false
    )
    assertEquals(PermissionOrigin.SAMSUNG_VENDOR, PermissionCatalogClassifier.classifyOrigin(samsung), "Samsung namespace should be detected")
    assertTrue(PermissionCatalogClassifier.isUndocumentedCandidate(samsung), "non-public Samsung entry should be discoverable")

    val writeSecure = PermissionCatalogEntry(
        name = "android.permission.WRITE_SECURE_SETTINGS",
        group = null,
        declaringPackage = "android",
        protection = PermissionProtection.SIGNATURE_PRIVILEGED,
        publicSdkConstant = true
    )
    val planner = PermissionGrantPlanner()
    val systemPlan = planner.plan(
        PermissionGrantContext(
            entry = writeSecure,
            alreadyGranted = false,
            dpcCanGrantRuntime = false,
            shizukuAvailable = true,
            samsungKnoxSpecialAvailable = false,
            systemPrivilegedAvailable = true,
            userActionAvailable = false,
            alternateCapabilityRouteAvailable = true
        )
    )
    assertEquals(PermissionGrantRoute.SYSTEM_PRIVILEGED, systemPlan.primary, "signature/privileged permission must not be treated as runtime-grantable")
    assertTrue(PermissionGrantRoute.LAB_HOOK_SIMULATION !in systemPlan.routes, "production grant plan must not use hook simulation")

    val overlay = PermissionCatalogEntry(
        name = "android.permission.SYSTEM_ALERT_WINDOW",
        group = null,
        declaringPackage = "android",
        protection = PermissionProtection.SPECIAL_ACCESS,
        publicSdkConstant = true
    )
    val overlayPlan = planner.plan(
        PermissionGrantContext(
            entry = overlay,
            alreadyGranted = false,
            dpcCanGrantRuntime = false,
            shizukuAvailable = true,
            samsungKnoxSpecialAvailable = true,
            systemPrivilegedAvailable = false,
            userActionAvailable = true,
            alternateCapabilityRouteAvailable = false
        )
    )
    assertEquals(PermissionGrantRoute.KNOX_SPECIAL_ACCESS, overlayPlan.primary, "Samsung Knox special access should outrank user settings when available")

    println("PermissionCatalogAndGrantPlannerTest: PASS")
}
