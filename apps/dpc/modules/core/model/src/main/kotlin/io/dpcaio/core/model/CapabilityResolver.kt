package io.dpcaio.core.model

object CapabilityResolver {
    fun resolve(
        requirements: CapabilityRequirements,
        context: ManagementContext,
    ): CapabilityResolution {
        val hardFailure = hardFailure(requirements, context)
        val availability = hardFailure ?: when (requirements.visibility) {
            VisibilityClass.LAB -> CapabilityAvailability.LAB_ONLY
            VisibilityClass.EXPERIMENTAL -> CapabilityAvailability.EXPERIMENTAL
            else -> CapabilityAvailability.AVAILABLE
        }

        val visible = when (requirements.visibility) {
            VisibilityClass.NORMAL -> availability == CapabilityAvailability.AVAILABLE || context.showHidden || context.developerMode
            VisibilityClass.HIDDEN -> context.showHidden || context.developerMode
            VisibilityClass.LAB -> context.developerMode
            VisibilityClass.EXPERIMENTAL -> context.showExperimental || context.developerMode
        }

        val executable = hardFailure == null && when (availability) {
            CapabilityAvailability.AVAILABLE -> true
            CapabilityAvailability.LAB_ONLY -> context.developerMode
            CapabilityAvailability.EXPERIMENTAL -> context.showExperimental || context.developerMode
            else -> false
        }

        return CapabilityResolution(
            availability = availability,
            visible = visible,
            executable = executable,
            reason = reason(availability, requirements, context),
        )
    }

    private fun hardFailure(
        requirements: CapabilityRequirements,
        context: ManagementContext,
    ): CapabilityAvailability? {
        if (context.apiLevel < requirements.minApi || requirements.maxApi?.let { context.apiLevel > it } == true) {
            return CapabilityAvailability.API_UNAVAILABLE
        }

        when (requirements.ownership) {
            OwnershipRequirement.ANY -> Unit
            OwnershipRequirement.DEVICE_OWNER -> if (context.ownership != OwnershipMode.DEVICE_OWNER) {
                return CapabilityAvailability.DEVICE_OWNER_ONLY
            }
            OwnershipRequirement.PROFILE_OWNER -> if (context.ownership != OwnershipMode.PROFILE_OWNER) {
                return CapabilityAvailability.PROFILE_OWNER_ONLY
            }
            OwnershipRequirement.DEVICE_OR_PROFILE_OWNER -> if (context.ownership == OwnershipMode.NONE) {
                return CapabilityAvailability.OWNER_REQUIRED
            }
            OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE -> {
                val allowed = context.ownership == OwnershipMode.DEVICE_OWNER ||
                    (context.ownership == OwnershipMode.PROFILE_OWNER && context.organizationOwnedProfile)
                if (!allowed) return CapabilityAvailability.ORG_OWNED_PROFILE_REQUIRED
            }
        }

        if (requirements.requiresSamsung && !context.samsungDevice) {
            return CapabilityAvailability.SAMSUNG_ONLY
        }
        if (requirements.requiresKnox && !context.knoxAvailable) {
            return CapabilityAvailability.SAMSUNG_ONLY
        }
        if (requirements.requiresKnoxLicense && !context.knoxLicenseActive) {
            return CapabilityAvailability.KNOX_LICENSE_REQUIRED
        }
        if (requirements.requiresAffiliation && !context.affiliatedUser) {
            return CapabilityAvailability.AFFILIATION_REQUIRED
        }
        return null
    }

    private fun reason(
        availability: CapabilityAvailability,
        requirements: CapabilityRequirements,
        context: ManagementContext,
    ): String? = when (availability) {
        CapabilityAvailability.AVAILABLE -> null
        CapabilityAvailability.API_UNAVAILABLE -> "Requires Android API ${requirements.minApi}+; current API ${context.apiLevel}"
        CapabilityAvailability.OWNER_REQUIRED -> "Requires Device Owner or Profile Owner"
        CapabilityAvailability.PROFILE_OWNER_ONLY -> "Requires Profile Owner"
        CapabilityAvailability.DEVICE_OWNER_ONLY -> "Requires Device Owner"
        CapabilityAvailability.ORG_OWNED_PROFILE_REQUIRED -> "Requires Device Owner or organization-owned Profile Owner"
        CapabilityAvailability.SAMSUNG_ONLY -> "Requires Samsung/Knox runtime"
        CapabilityAvailability.KNOX_LICENSE_REQUIRED -> "Requires an active Knox license"
        CapabilityAvailability.LAB_ONLY -> "Developer / Lab mode"
        CapabilityAvailability.EXPERIMENTAL -> "Experimental feature"
        CapabilityAvailability.UNSUPPORTED -> "Unsupported on this device"
        CapabilityAvailability.AFFILIATION_REQUIRED -> "Requires an affiliated managed user/device context"
        CapabilityAvailability.DELEGATION_REQUIRED -> "Required delegation scope is missing"
        CapabilityAvailability.ERROR -> "Capability evaluation failed"
        else -> availability.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
