package io.dpcaio.knox.license

enum class KnoxLicenseState {
    UNKNOWN,
    OFFLINE_PENDING,
    ACTIVATING,
    ACTIVE,
    FAILED_BINDING,
    FAILED_TERMINATED,
    FAILED
}

enum class KnoxActivationAction {
    NOT_SAMSUNG,
    WAIT_FOR_DEVICE_OWNER,
    NEED_VALID_KEY,
    QUEUE_FOR_NETWORK,
    ACTIVATE_NOW,
    KEEP_ACTIVE
}

data class KnoxActivationInput(
    val isSamsung: Boolean,
    val isDeviceOwner: Boolean,
    val hasValidKeyConfigured: Boolean,
    val networkAvailable: Boolean,
    val currentState: KnoxLicenseState
)

class KnoxActivationPlanner {
    fun plan(input: KnoxActivationInput): KnoxActivationAction = when {
        !input.isSamsung -> KnoxActivationAction.NOT_SAMSUNG
        !input.isDeviceOwner -> KnoxActivationAction.WAIT_FOR_DEVICE_OWNER
        input.currentState == KnoxLicenseState.ACTIVE -> KnoxActivationAction.KEEP_ACTIVE
        !input.hasValidKeyConfigured -> KnoxActivationAction.NEED_VALID_KEY
        !input.networkAvailable -> KnoxActivationAction.QUEUE_FOR_NETWORK
        else -> KnoxActivationAction.ACTIVATE_NOW
    }
}

class KnoxLicenseResultInterpreter {
    fun fromErrorCode(errorCode: Int): KnoxLicenseState = when (errorCode) {
        0 -> KnoxLicenseState.ACTIVE
        501, 502 -> KnoxLicenseState.OFFLINE_PENDING
        206 -> KnoxLicenseState.FAILED_BINDING
        203 -> KnoxLicenseState.FAILED_TERMINATED
        else -> KnoxLicenseState.FAILED
    }
}
