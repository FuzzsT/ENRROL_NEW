package io.dpcaio.policy

enum class LifecycleRisk { NONE, LOW, MEDIUM, HIGH, CRITICAL }

data class LockTaskPolicySpec(
    val packages: Set<String>,
    val featureMask: Int,
) {
    fun valid(): Boolean = packages.all { PACKAGE.matches(it) }
    companion object { private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$") }
}

data class DeviceSecurityPolicySpec(
    val passwordComplexity: Int? = null,
    val maxFailedPasswordsForWipe: Int = 0,
    val keyguardDisabledFeatures: Int? = null,
    val cameraDisabled: Boolean? = null,
    val screenCaptureDisabled: Boolean? = null,
) {
    val wipeRisk: LifecycleRisk
        get() = if (maxFailedPasswordsForWipe > 0) LifecycleRisk.CRITICAL else LifecycleRisk.NONE
}

enum class AppControlAction {
    HIDE,
    SUSPEND,
    BLOCK_UNINSTALL,
    SET_RESTRICTIONS,
    CLEAR_DATA,
    DISABLE_USER_CONTROL,
}

data class AppControlRequest(
    val packageName: String,
    val action: AppControlAction,
) {
    val risk: LifecycleRisk
        get() = when (action) {
            AppControlAction.CLEAR_DATA -> LifecycleRisk.HIGH
            AppControlAction.BLOCK_UNINSTALL,
            AppControlAction.DISABLE_USER_CONTROL -> LifecycleRisk.MEDIUM
            else -> LifecycleRisk.LOW
        }
}

data class FrpPolicySpec(
    val enabled: Boolean,
    val accountIds: List<String>,
) {
    fun valid(): Boolean = accountIds.all { it.isNotBlank() && it.length <= 256 }
}
