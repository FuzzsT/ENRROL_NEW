package io.dpcaio.policy.parity

data class ParityRuntimeFacts(
    val sdkInt: Int,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val isCope: Boolean,
    val features: Set<PlatformFeature> = emptySet(),
    val delegatedScopes: Set<String> = emptySet(),
)

sealed class ParityAvailability {
    data object Available : ParityAvailability()
    data class Unavailable(val reason: String) : ParityAvailability()
    data class Deprecated(val reason: String, val replacement: String? = null) : ParityAvailability()
}

object TestDpcCapabilityResolver {
    fun resolve(entry: TestDpcParityEntry, facts: ParityRuntimeFacts): ParityAvailability {
        if (entry.deprecated || entry.implementationState == TestDpcImplementationState.DEPRECATED_UNAVAILABLE) {
            return ParityAvailability.Deprecated(
                reason = entry.unavailableReason ?: "Deprecated Android enterprise behavior",
                replacement = entry.replacementGuidance,
            )
        }

        if (facts.sdkInt < entry.minSdk) {
            return ParityAvailability.Unavailable("Requires Android API ${entry.minSdk}+; device API is ${facts.sdkInt}")
        }

        val ownerAvailable = when (entry.ownerRequirement) {
            OwnerRequirement.NONE -> true
            OwnerRequirement.DEVICE_OWNER -> facts.isDeviceOwner
            OwnerRequirement.PROFILE_OWNER -> facts.isProfileOwner
            OwnerRequirement.DEVICE_OR_PROFILE_OWNER -> facts.isDeviceOwner || facts.isProfileOwner
            OwnerRequirement.COPE -> facts.isCope
        }
        if (!ownerAvailable) {
            return ParityAvailability.Unavailable("Requires ${entry.ownerRequirement.humanLabel()}")
        }

        val missingFeatures = entry.requiredFeatures - facts.features
        if (missingFeatures.isNotEmpty()) {
            return ParityAvailability.Unavailable(
                "Missing device feature(s): ${missingFeatures.joinToString { it.name }}"
            )
        }

        val missingScopes = entry.requiredDelegatedScopes - facts.delegatedScopes
        if (missingScopes.isNotEmpty()) {
            return ParityAvailability.Unavailable(
                "Missing delegated scope(s): ${missingScopes.sorted().joinToString()}"
            )
        }

        if (entry.handlerId == null && entry.destination == null && !entry.unavailableReason.isNullOrBlank()) {
            return ParityAvailability.Unavailable(entry.unavailableReason)
        }

        return ParityAvailability.Available
    }

    private fun OwnerRequirement.humanLabel(): String = when (this) {
        OwnerRequirement.NONE -> "no owner role"
        OwnerRequirement.DEVICE_OWNER -> "Device Owner"
        OwnerRequirement.PROFILE_OWNER -> "Profile Owner"
        OwnerRequirement.DEVICE_OR_PROFILE_OWNER -> "Device Owner or Profile Owner"
        OwnerRequirement.COPE -> "COPE"
    }
}
