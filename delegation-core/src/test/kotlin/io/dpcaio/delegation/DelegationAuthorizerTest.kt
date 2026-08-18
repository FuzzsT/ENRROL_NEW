package io.dpcaio.delegation

private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }
private fun assertFalse(value: Boolean, message: String) { if (value) error(message) }
private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val registered = DelegatedClient(
        identity = ClientIdentity("com.example.client", 10123, 0, "AA:BB"),
        scopes = setOf("packages.read", "packages.visibility"),
        enabled = true,
        blocked = false
    )
    val registry = InMemoryClientRegistry(listOf(registered))
    val authorizer = DelegationAuthorizer(registry)

    val exact = authorizer.authorize(registered.identity, "packages.visibility")
    assertTrue(exact.allowed, "exact identity with granted scope must be authorized")
    assertEquals(AuthorizationReason.ALLOWED, exact.reason, "success reason")

    val wrongCert = authorizer.authorize(registered.identity.copy(certificateSha256 = "CC:DD"), "packages.visibility")
    assertFalse(wrongCert.allowed, "same package/uid with different certificate must be denied")
    assertEquals(AuthorizationReason.IDENTITY_MISMATCH, wrongCert.reason, "certificate mismatch reason")

    val wrongUser = authorizer.authorize(registered.identity.copy(userId = 10), "packages.visibility")
    assertFalse(wrongUser.allowed, "client authorization must be user/profile aware")

    val missingScope = authorizer.authorize(registered.identity, "apps.install")
    assertFalse(missingScope.allowed, "missing scope must deny request")
    assertEquals(AuthorizationReason.SCOPE_DENIED, missingScope.reason, "scope denial reason")

    val blockedRegistry = InMemoryClientRegistry(listOf(registered.copy(blocked = true)))
    assertEquals(
        AuthorizationReason.CLIENT_BLOCKED,
        DelegationAuthorizer(blockedRegistry).authorize(registered.identity, "packages.read").reason,
        "blocked client must never authorize"
    )

    println("DelegationAuthorizerTest: PASS")
}
