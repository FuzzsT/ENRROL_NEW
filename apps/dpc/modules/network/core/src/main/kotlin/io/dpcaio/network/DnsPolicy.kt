package io.dpcaio.network

sealed interface DnsAction {
    data object Block : DnsAction
    data class Override(val address: String) : DnsAction
}
data class DnsRule(val pattern: String, val action: DnsAction)

class DnsRuleSet(rules: List<DnsRule>) {
    private val normalized = rules.map { it.copy(pattern = it.pattern.lowercase()) }
    fun resolve(host: String): DnsAction? {
        val h = host.lowercase().trimEnd('.')
        return normalized.firstOrNull { rule ->
            val p = rule.pattern
            if (p.startsWith("*.")) h.endsWith(p.removePrefix("*")) && h != p.removePrefix("*.")
            else h == p
        }?.action
    }
}

enum class DnsRoute { PRIVATE_DNS_DOT, DOH_DIAGNOSTIC, SYSTEM_DEFAULT }
data class DnsPolicyInput(val deviceOwner: Boolean, val specifiedPrivateDnsSupported: Boolean, val dohDiagnosticEnabled: Boolean)
class DnsPolicyPlanner {
    fun plan(input: DnsPolicyInput): DnsRoute = when {
        input.deviceOwner && input.specifiedPrivateDnsSupported -> DnsRoute.PRIVATE_DNS_DOT
        input.dohDiagnosticEnabled -> DnsRoute.DOH_DIAGNOSTIC
        else -> DnsRoute.SYSTEM_DEFAULT
    }
}
