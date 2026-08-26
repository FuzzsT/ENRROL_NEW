package io.dpcaio.policy

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val success = PolicyResult.success(value = setOf("com.example.failed"))
    assertEquals(PolicyStatus.SUCCESS, success.status, "success status")
    assertTrue(success.isSuccess, "success helper")
    assertEquals(setOf("com.example.failed"), success.value, "success payload")

    val denied = PolicyResult.failure<Unit>(
        status = PolicyStatus.NOT_DEVICE_OWNER,
        message = "device owner authority required"
    )
    assertEquals(PolicyStatus.NOT_DEVICE_OWNER, denied.status, "authority failure status")
    assertTrue(!denied.isSuccess, "failure must not be success")

    val platform = PolicyResult.failure<Unit>(
        status = PolicyStatus.PLATFORM_REJECTED,
        message = "setPermissionGrantState returned false"
    )
    assertEquals(PolicyStatus.PLATFORM_REJECTED, platform.status, "platform rejection status")

    println("PolicyResultTest: PASS")
}
