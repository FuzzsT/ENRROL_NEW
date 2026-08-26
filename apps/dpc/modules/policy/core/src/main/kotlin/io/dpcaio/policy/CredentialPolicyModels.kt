package io.dpcaio.policy

enum class CertificateState {
    INSTALLED_USER_CA,
    EXPIRED,
    INVALID,
}

data class CaCertificateRecord(
    val sha256: String,
    val subject: String,
    val issuer: String,
    val notAfterEpochMs: Long,
    val state: CertificateState = CertificateState.INSTALLED_USER_CA,
)

data class KeyPairGrantSummary(
    val alias: String,
    val grantsByUid: Map<Int, Set<String>>,
) {
    val hasSharedUidGrant: Boolean get() = grantsByUid.values.any { it.size > 1 }
    val sharedUidPackages: Set<String> get() = grantsByUid.values.filter { it.size > 1 }.flatten().toSet()
}

object CredentialPolicyValidator {
    private val aliasPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
    fun validAlias(alias: String): Boolean = aliasPattern.matches(alias)
}
