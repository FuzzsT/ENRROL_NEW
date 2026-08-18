package io.dpcaio.policy

interface DelegationPolicyGateway {
    fun getDelegatedScopes(packageName: String): PolicyResult<Set<String>>
    fun setDelegatedScopes(packageName: String, scopes: Set<String>): PolicyResult<Unit>
}
