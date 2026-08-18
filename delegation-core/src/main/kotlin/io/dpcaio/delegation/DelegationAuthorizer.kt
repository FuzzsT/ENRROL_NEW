package io.dpcaio.delegation

class DelegationAuthorizer(private val registry: ClientRegistry) {
    fun authorize(caller: ClientIdentity, requiredScope: String): AuthorizationResult {
        val registered = registry.findByPackage(caller.packageName)
            ?: return AuthorizationResult(false, AuthorizationReason.CLIENT_NOT_FOUND)

        if (registered.identity != caller) {
            return AuthorizationResult(false, AuthorizationReason.IDENTITY_MISMATCH)
        }
        if (!registered.enabled) {
            return AuthorizationResult(false, AuthorizationReason.CLIENT_DISABLED)
        }
        if (registered.blocked) {
            return AuthorizationResult(false, AuthorizationReason.CLIENT_BLOCKED)
        }
        if (requiredScope !in registered.scopes) {
            return AuthorizationResult(false, AuthorizationReason.SCOPE_DENIED)
        }
        return AuthorizationResult(true, AuthorizationReason.ALLOWED)
    }
}
