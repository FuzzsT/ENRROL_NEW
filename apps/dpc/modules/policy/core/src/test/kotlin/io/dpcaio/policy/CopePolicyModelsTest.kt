package io.dpcaio.policy

fun main() {
    check(CopePolicyValidator.validMaximumTimeOff(0L))
    check(CopePolicyValidator.validMaximumTimeOff(72L * 60L * 60L * 1000L))
    check(!CopePolicyValidator.validMaximumTimeOff(71L * 60L * 60L * 1000L))

    check(CopePolicyValidator.validOrganizationId("corp-001"))
    check(!CopePolicyValidator.validOrganizationId("abc"))
    check(!CopePolicyValidator.validOrganizationId("x".repeat(65)))

    val ids = CopePolicyValidator.normalizeAffiliationIds(setOf(" corp-a ", "corp-a", "corp-b", " "))
    check(ids == setOf("corp-a", "corp-b"))


    val contacts = ManagedProfilePackagePolicySpec(
        PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM,
        setOf("com.example.contacts"),
    )
    check(contacts.valid())
    check(!ManagedProfilePackagePolicySpec(PackageAccessPolicyType.ALLOWLIST, setOf("bad package")).valid())

    println("CopePolicyModelsTest: PASS")
}
