package io.dpcaio.policy.parity

object TestDpcCapabilityResolverTest {
    @JvmStatic
    fun main(args: Array<String>) {
        deprecatedWins()
        sdkGatePrecedesOwnerGate()
        ownerGateWorks()
        featureGateWorks()
        delegatedScopeGateWorks()
        availableWhenAllRequirementsMatch()
        println("PASS: TestDPC capability resolver")
    }

    private fun baseEntry() = TestDpcParityEntry(
        id = "testdpc.sample",
        testDpcKey = "sample",
        googleTitle = "Sample",
        category = "test",
        implementationState = TestDpcImplementationState.IMPLEMENT_PUBLIC_API,
        handlerId = "sample.handler",
    )

    private fun facts(
        sdk: Int = 37,
        deviceOwner: Boolean = true,
        profileOwner: Boolean = false,
        cope: Boolean = false,
        features: Set<PlatformFeature> = emptySet(),
        scopes: Set<String> = emptySet(),
    ) = ParityRuntimeFacts(sdk, deviceOwner, profileOwner, cope, features, scopes)

    private fun deprecatedWins() {
        val entry = baseEntry().copy(
            deprecated = true,
            unavailableReason = "Deprecated",
            replacementGuidance = "Use replacement",
        )
        check(TestDpcCapabilityResolver.resolve(entry, facts(sdk = 1)) is ParityAvailability.Deprecated)
    }

    private fun sdkGatePrecedesOwnerGate() {
        val entry = baseEntry().copy(minSdk = 35, ownerRequirement = OwnerRequirement.PROFILE_OWNER)
        val result = TestDpcCapabilityResolver.resolve(entry, facts(sdk = 34, deviceOwner = false))
        check(result is ParityAvailability.Unavailable && result.reason.contains("API 35"))
    }

    private fun ownerGateWorks() {
        val entry = baseEntry().copy(ownerRequirement = OwnerRequirement.PROFILE_OWNER)
        val result = TestDpcCapabilityResolver.resolve(entry, facts(deviceOwner = true, profileOwner = false))
        check(result is ParityAvailability.Unavailable && result.reason.contains("Profile Owner"))
    }

    private fun featureGateWorks() {
        val entry = baseEntry().copy(requiredFeatures = setOf(PlatformFeature.WIFI))
        val result = TestDpcCapabilityResolver.resolve(entry, facts(features = emptySet()))
        check(result is ParityAvailability.Unavailable && result.reason.contains("WIFI"))
    }

    private fun delegatedScopeGateWorks() {
        val entry = baseEntry().copy(requiredDelegatedScopes = setOf("delegation-cert-install"))
        val result = TestDpcCapabilityResolver.resolve(entry, facts(scopes = emptySet()))
        check(result is ParityAvailability.Unavailable && result.reason.contains("delegation-cert-install"))
    }

    private fun availableWhenAllRequirementsMatch() {
        val entry = baseEntry().copy(
            ownerRequirement = OwnerRequirement.DEVICE_OR_PROFILE_OWNER,
            requiredFeatures = setOf(PlatformFeature.WIFI),
            requiredDelegatedScopes = setOf("delegation-cert-install"),
        )
        val result = TestDpcCapabilityResolver.resolve(
            entry,
            facts(features = setOf(PlatformFeature.WIFI), scopes = setOf("delegation-cert-install")),
        )
        check(result == ParityAvailability.Available)
    }
}
