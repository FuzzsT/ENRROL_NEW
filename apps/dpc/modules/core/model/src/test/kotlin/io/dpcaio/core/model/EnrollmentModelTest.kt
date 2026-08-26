package io.dpcaio.core.model

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val config = NormalizedEnrollmentConfig(
        source = EnrollmentSource.QR,
        requestedMode = "work-profile",
        enrollmentToken = "secret-token",
        policyProfile = "enterprise-default",
        serverUri = "https://enroll.example.test",
        organizationId = "example-org",
        allowOffline = false,
    )
    val session = EnrollmentSession.new(config, nowMillis = 1000L)
    assertEquals(EnrollmentStage.RECEIVED, session.stage, "new session stage")
    assertEquals("enterprise-default", session.policyProfile, "profile preserved")
    assertEquals(5_000L, EnrollmentRetryPolicy.delayMillis(1), "retry 1")
    assertEquals(15_000L, EnrollmentRetryPolicy.delayMillis(2), "retry 2")
    assertEquals(30_000L, EnrollmentRetryPolicy.delayMillis(3), "retry 3")
    assertEquals(120_000L, EnrollmentRetryPolicy.delayMillis(4), "retry 4")
    assertEquals(null, EnrollmentRetryPolicy.delayMillis(5), "retry exhaustion")
    assertTrue(EnrollmentErrorCode.SERVER_UNREACHABLE.retryable, "server failure retryable")
    assertEquals(EnrollmentStage.WAITING_FOR_RETRY, session.fail(EnrollmentErrorCode.SERVER_UNREACHABLE, 2000L).stage, "retryable failure stage")
    assertTrue(!EnrollmentErrorCode.TOKEN_EXPIRED.retryable, "expired token terminal")
    println("EnrollmentModelTest: PASS")
}
