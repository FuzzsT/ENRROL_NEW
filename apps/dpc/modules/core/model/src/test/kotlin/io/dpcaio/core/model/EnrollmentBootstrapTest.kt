package io.dpcaio.core.model

private fun assertEquals(expected: Any?, actual: Any?, message: String) { if (expected != actual) error("$message: expected=$expected actual=$actual") }
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val policy = BootstrapPolicy(
        schemaVersion = 1,
        profileId = "corporate",
        allowedModes = setOf("work-profile"),
        minimumAndroidApi = 29,
        minimumDpcVersion = "0.9.0",
        requiredCapabilities = setOf("profile-owner-or-device-owner"),
        baseline = BootstrapBaseline(autoTime = true, networkLogging = false, securityLogging = false),
    )
    assertTrue(policy.validate(requestedMode = "work-profile", androidApi = 37, dpcVersion = "0.9.0").ok, "compatible bootstrap")
    assertEquals("MODE_NOT_ALLOWED", policy.validate("fully-managed", 37, "0.9.0").errorCode, "mode rejected")
    assertEquals("ANDROID_API_TOO_OLD", policy.validate("work-profile", 28, "0.9.0").errorCode, "api floor")
    assertEquals("UNSUPPORTED_SCHEMA", policy.copy(schemaVersion = 2).validate("work-profile", 37, "0.9.0").errorCode, "schema floor")
    println("EnrollmentBootstrapTest: PASS")
}
