package io.dpcaio.app

object ProvisioningModeSelector {
    const val EXTRA_REQUESTED_MODE = "io.dpcaio.extra.PROVISIONING_MODE"
    const val MODE_AUTO = "auto"
    const val MODE_WORK_PROFILE = "work-profile"
    const val MODE_FULLY_MANAGED = "fully-managed"

    fun select(
        requestedMode: String?,
        allowedModes: List<Int>,
        fullyManagedMode: Int,
        managedProfileMode: Int,
    ): Int? {
        val effectiveAllowedModes = allowedModes.ifEmpty {
            listOf(managedProfileMode, fullyManagedMode)
        }
        return when (requestedMode?.lowercase() ?: MODE_AUTO) {
            MODE_WORK_PROFILE -> managedProfileMode.takeIf { it in effectiveAllowedModes }
            MODE_FULLY_MANAGED -> fullyManagedMode.takeIf { it in effectiveAllowedModes }
            MODE_AUTO -> when {
                fullyManagedMode in effectiveAllowedModes -> fullyManagedMode
                managedProfileMode in effectiveAllowedModes -> managedProfileMode
                else -> null
            }
            else -> null
        }
    }
}
