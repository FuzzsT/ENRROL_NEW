package io.dpcaio.platform

private fun assertEquals(expected: Int, actual: Int, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    assertEquals(0, AndroidUserId.fromUid(10000), "system user app uid")
    assertEquals(10, AndroidUserId.fromUid(1_010_123), "secondary user uid")
    assertEquals(150, AndroidUserId.fromUid(15_000_999), "large user id")
    assertEquals(1_000_000, AndroidUserId.baseUid(10), "secondary user base uid")
    println("AndroidUserIdTest: PASS")
}
