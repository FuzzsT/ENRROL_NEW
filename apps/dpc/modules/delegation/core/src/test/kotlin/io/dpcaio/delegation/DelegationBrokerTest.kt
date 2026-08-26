package io.dpcaio.delegation

private fun assertBrokerEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private fun assertBrokerTrue(value: Boolean, message: String) { if (!value) error(message) }
private fun assertBrokerFalse(value: Boolean, message: String) { if (value) error(message) }

fun main() {
    val identity = ClientIdentity("com.example.client", 10123, 0, "AA:BB")
    val registry = InMemoryClientRegistry(listOf(
        DelegatedClient(identity, setOf("packages.visibility", "activities.launch"), enabled = true, blocked = false)
    ))
    val calls = mutableListOf<DelegatedRequest>()
    val executor = DelegatedOperationExecutor { request ->
        calls += request
        DelegatedExecutionResult(success = true, detail = "ok")
    }
    val broker = DelegationBroker(DelegationAuthorizer(registry), executor)

    val allowed = broker.execute(
        DelegatedRequest(identity, DelegatedOperation.PACKAGE_VISIBILITY, targetPackage = "com.target")
    )
    assertBrokerTrue(allowed.success, "authorized scoped request must execute")
    assertBrokerEquals(DelegationResultCode.EXECUTED, allowed.code, "authorized result code")
    assertBrokerEquals(1, calls.size, "executor called once")

    val denied = broker.execute(
        DelegatedRequest(identity, DelegatedOperation.APP_INSTALL, targetPackage = "com.target")
    )
    assertBrokerFalse(denied.success, "missing scope must fail")
    assertBrokerEquals(DelegationResultCode.AUTHORIZATION_DENIED, denied.code, "scope denial result")
    assertBrokerEquals(1, calls.size, "denied request must not reach executor")

    val mismatch = broker.execute(
        DelegatedRequest(identity.copy(certificateSha256 = "WRONG"), DelegatedOperation.PACKAGE_VISIBILITY)
    )
    assertBrokerEquals(DelegationResultCode.AUTHORIZATION_DENIED, mismatch.code, "identity mismatch denied")

    val failing = DelegationBroker(DelegationAuthorizer(registry), DelegatedOperationExecutor {
        DelegatedExecutionResult(success = false, detail = "platform rejected")
    }).execute(DelegatedRequest(identity, DelegatedOperation.ACTIVITY_LAUNCH))
    assertBrokerEquals(DelegationResultCode.EXECUTION_FAILED, failing.code, "executor failure preserved")
    assertBrokerEquals("platform rejected", failing.detail, "executor detail preserved")

    println("DelegationBrokerTest: PASS")
}
