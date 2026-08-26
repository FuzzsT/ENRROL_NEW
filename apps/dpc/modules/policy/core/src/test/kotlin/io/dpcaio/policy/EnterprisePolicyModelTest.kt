package io.dpcaio.policy

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    assertEquals(listOf("NOT_CONTROLLED", "DISABLED", "ENABLED"), TriStatePolicy.entries.map { it.name }, "tri-state names")
    assertEquals(
        listOf("NOT_CONTROLLED", "DISABLED", "DISABLED_CROSS_PROFILE"),
        AppFunctionsPolicy.entries.map { it.name },
        "app function names",
    )
    assertEquals(
        setOf("THREAD_NETWORK", "NFC_RADIO", "NFC_RADIO_CHANGES"),
        DeviceRestriction.entries.map { it.name }.toSet(),
        "restriction names",
    )
    check(EnterpriseDevicePolicyGateway::class.java.isAssignableFrom(DevicePolicyGateway::class.java))
    println("EnterprisePolicyModelTest: PASS")
}
