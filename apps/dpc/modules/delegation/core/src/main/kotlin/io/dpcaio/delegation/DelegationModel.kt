package io.dpcaio.delegation

data class ClientIdentity(
    val packageName: String,
    val uid: Int,
    val userId: Int,
    val certificateSha256: String
)

data class DelegatedClient(
    val identity: ClientIdentity,
    val scopes: Set<String>,
    val enabled: Boolean,
    val blocked: Boolean
)

enum class AuthorizationReason {
    ALLOWED,
    CLIENT_NOT_FOUND,
    IDENTITY_MISMATCH,
    CLIENT_DISABLED,
    CLIENT_BLOCKED,
    SCOPE_DENIED
}

data class AuthorizationResult(
    val allowed: Boolean,
    val reason: AuthorizationReason
)

interface ClientRegistry {
    fun findByPackage(packageName: String): DelegatedClient?
}

class InMemoryClientRegistry(clients: List<DelegatedClient>) : ClientRegistry {
    private val byPackage = clients.associateBy { it.identity.packageName }
    override fun findByPackage(packageName: String): DelegatedClient? = byPackage[packageName]
}
