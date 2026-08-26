package io.dpcaio.policy

data class CopePolicySnapshot(
    val crossProfilePackages: Set<String> = emptySet(),
    val maximumTimeOffMillis: Long = 0L,
    val personalAppsSuspended: Boolean = false,
    val organizationName: String? = null,
    val affiliationIds: Set<String> = emptySet(),
)

object CopePolicyValidator {
    const val MIN_TIME_OFF_MILLIS = 72L * 60L * 60L * 1000L

    fun validMaximumTimeOff(timeoutMillis: Long): Boolean =
        timeoutMillis == 0L || timeoutMillis >= MIN_TIME_OFF_MILLIS

    fun validOrganizationId(id: String): Boolean = id.length in 6..64 && id.trim() == id

    fun normalizeAffiliationIds(ids: Set<String>): Set<String> =
        ids.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

enum class PackageAccessPolicyType {
    UNRESTRICTED,
    ALLOWLIST,
    ALLOWLIST_AND_SYSTEM,
    BLOCKLIST,
}

data class ManagedProfilePackagePolicySpec(
    val type: PackageAccessPolicyType,
    val packageNames: Set<String> = emptySet(),
) {
    fun valid(): Boolean {
        if (type == PackageAccessPolicyType.UNRESTRICTED) return packageNames.isEmpty()
        val packagePattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        return packageNames.all { packagePattern.matches(it) }
    }
}
