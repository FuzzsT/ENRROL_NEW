package io.dpcaio.platform

private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }
private fun assertFalse(value: Boolean, message: String) { if (value) error(message) }
private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val gate = CompatibilityGate()

    for (api in listOf(29, 37)) {
        val result = gate.evaluate(PlatformProfile(api, "arm64-v8a", is64Bit = true, pageSize = 4096))
        assertTrue(result.supported, "API $api should be supported")
    }

    assertFalse(
        gate.evaluate(PlatformProfile(28, "arm64-v8a", true, 4096)).supported,
        "Android 9/API 28 must be outside the supported product baseline"
    )

    val abis = mapOf(
        "arm64-v8a" to true,
        "armeabi-v7a" to false,
        "x86_64" to true,
        "x86" to false
    )
    for ((abi, bit64) in abis) {
        val result = gate.evaluate(PlatformProfile(35, abi, bit64, 16384))
        assertTrue(result.supported, "$abi with 16 KiB pages should pass the baseline gate")
    }

    val badAbi = gate.evaluate(PlatformProfile(35, "mips", true, 4096))
    assertFalse(badAbi.supported, "unsupported ABI must fail")
    assertTrue(badAbi.findings.any { it.code == "UNSUPPORTED_ABI" }, "ABI failure must be explicit")

    val badPage = gate.evaluate(PlatformProfile(35, "arm64-v8a", true, 8192))
    assertFalse(badPage.supported, "unsupported page size must fail")
    assertEquals("UNSUPPORTED_PAGE_SIZE", badPage.findings.first { !it.passed }.code,
        "page-size finding should identify the failed gate")

    println("CompatibilityGateTest: PASS")
}
