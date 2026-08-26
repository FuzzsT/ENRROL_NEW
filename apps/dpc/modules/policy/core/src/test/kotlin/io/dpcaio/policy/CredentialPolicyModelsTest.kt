package io.dpcaio.policy

fun main() {
    check(CredentialPolicyValidator.validAlias("vpn-client-2026"))
    check(!CredentialPolicyValidator.validAlias(""))
    check(!CredentialPolicyValidator.validAlias(" bad alias "))

    val grant = KeyPairGrantSummary(
        alias = "vpn-client-2026",
        grantsByUid = mapOf(10001 to setOf("com.example.vpn"), 10002 to setOf("com.a", "com.b"))
    )
    check(grant.hasSharedUidGrant)
    check(grant.sharedUidPackages == setOf("com.a", "com.b"))

    val ca = CaCertificateRecord(
        sha256 = "AA:BB",
        subject = "CN=Example",
        issuer = "CN=Example",
        notAfterEpochMs = 123L,
    )
    check(ca.state == CertificateState.INSTALLED_USER_CA)

    println("CredentialPolicyModelsTest: PASS")
}
