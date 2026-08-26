package io.dpcaio.oem

fun main() {
    val breaker = OemCircuitBreaker(threshold = 3)
    check(!breaker.isOpen("cap"))
    breaker.recordFailure("cap", "SecurityException")
    breaker.recordFailure("cap", "SecurityException")
    check(!breaker.isOpen("cap"))
    breaker.recordFailure("cap", "SecurityException")
    check(breaker.isOpen("cap"))

    breaker.recordSuccess("cap")
    check(!breaker.isOpen("cap"))

    breaker.recordFailure("cap", "SecurityException")
    breaker.recordFailure("cap", "NoSuchMethodException")
    breaker.recordFailure("cap", "NoSuchMethodException")
    check(!breaker.isOpen("cap")) // failure key changed; count restarted
    println("OemCircuitBreakerTest: PASS")
}
