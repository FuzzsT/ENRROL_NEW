package io.dpcaio.policy

enum class CredentialRecoveryState {
    NOT_PROVISIONED,
    PROVISIONED_PENDING_CONFIRMATION,
    ACTIVE,
    ROTATING,
    REVOKED,
    LOST_BEFORE_ACTIVATION,
    UNSUPPORTED_SECURE_LOCK_SCREEN,
}

data class CredentialRecoveryEvidence(
    val provisionedMarker: Boolean,
    val tokenActive: Boolean,
    val vaultEntryPresent: Boolean,
    val revokedMarker: Boolean,
    val secureLockSupported: Boolean,
    val rotating: Boolean = false,
)

class CredentialRecoveryPlanner {
    fun state(e: CredentialRecoveryEvidence): CredentialRecoveryState = when {
        !e.secureLockSupported -> CredentialRecoveryState.UNSUPPORTED_SECURE_LOCK_SCREEN
        e.rotating -> CredentialRecoveryState.ROTATING
        e.revokedMarker -> CredentialRecoveryState.REVOKED
        e.provisionedMarker && !e.vaultEntryPresent -> CredentialRecoveryState.LOST_BEFORE_ACTIVATION
        e.provisionedMarker && e.tokenActive && e.vaultEntryPresent -> CredentialRecoveryState.ACTIVE
        e.provisionedMarker && e.vaultEntryPresent -> CredentialRecoveryState.PROVISIONED_PENDING_CONFIRMATION
        else -> CredentialRecoveryState.NOT_PROVISIONED
    }
}

data class CredentialRecoverySnapshot(
    val state: CredentialRecoveryState,
    val userId: Int,
    val adminId: String,
    val tokenStoredEncrypted: Boolean,
    val tokenActive: Boolean,
    val detail: String,
)
