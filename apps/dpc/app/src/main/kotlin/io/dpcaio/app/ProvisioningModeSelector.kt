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
    ): Int? = when (requestedMode?.lowercase() ?: MODE_AUTO) {
        MODE_WORK_PROFILE -> managedProfileMode.takeIf { it in allowedModes }
        MODE_FULLY_MANAGED -> fullyManagedMode.takeIf { it in allowedModes }
        MODE_AUTO -> when {
            fullyManagedMode in allowedModes -> fullyManagedMode
            managedProfileMode in allowedModes -> managedProfileMode
            else -> null
        }
        else -> null
    }
}
