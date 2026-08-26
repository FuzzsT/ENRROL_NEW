package io.dpcaio.oem

class OemCircuitBreaker(
    private val threshold: Int = 3,
) {
    private val failures = mutableMapOf<String, Pair<String, Int>>()

    fun isOpen(capabilityId: String): Boolean = failures[capabilityId]?.second?.let { it >= threshold } == true

    fun recordFailure(capabilityId: String, failureKey: String) {
        val previous = failures[capabilityId]
        val count = if (previous?.first == failureKey) previous.second + 1 else 1
        failures[capabilityId] = failureKey to count
    }

    fun recordSuccess(capabilityId: String) {
        failures.remove(capabilityId)
    }

    fun reset() = failures.clear()
}
