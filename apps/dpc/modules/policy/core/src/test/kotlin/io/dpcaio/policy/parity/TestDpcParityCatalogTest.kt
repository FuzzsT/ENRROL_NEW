package io.dpcaio.policy.parity

object TestDpcParityCatalogTest {
    @JvmStatic
    fun main() {
        val entries = TestDpcParityCatalog.entries
        check(entries.size == 169)
        check(entries.map { it.testDpcKey }.toSet().size == 169)
        check(entries.map { it.id }.toSet().size == 169)
        entries.forEach { entry ->
            when (entry.implementationState) {
                TestDpcImplementationState.NATIVE -> check(entry.destination != null)
                TestDpcImplementationState.EXPOSE_BACKEND,
                TestDpcImplementationState.IMPLEMENT_PUBLIC_API,
                TestDpcImplementationState.MODERN_EQUIVALENT,
                TestDpcImplementationState.DEVICE_CAPABILITY_REQUIRED ->
                    check(entry.handlerId != null || entry.destination != null || entry.unavailableReason != null)
                TestDpcImplementationState.DEPRECATED_UNAVAILABLE -> check(!entry.unavailableReason.isNullOrBlank())
            }
        }
        println("PASS: TestDPC parity catalog core")
    }
}
