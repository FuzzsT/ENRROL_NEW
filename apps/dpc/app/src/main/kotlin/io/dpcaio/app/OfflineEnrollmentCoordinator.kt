package io.dpcaio.app

import android.content.Context
import android.content.Intent
import io.dpcaio.core.model.NormalizedEnrollmentConfig
import io.dpcaio.offline.OfflineStage

sealed interface OfflineEnrollmentOutcome {
    data class Complete(val stage: OfflineStage) : OfflineEnrollmentOutcome
    data class Retryable(val code: String, val message: String) : OfflineEnrollmentOutcome
    data class Failed(val code: String, val message: String) : OfflineEnrollmentOutcome
}

class OfflineEnrollmentCoordinator(private val context: Context) {
    fun resumeOrCreate(intent: Intent, config: NormalizedEnrollmentConfig): OfflineEnrollmentOutcome {
        val bundleId = config.offlineBundleId?.takeIf { it.isNotBlank() }
            ?: return OfflineEnrollmentOutcome.Failed("OFFLINE_BUNDLE_REQUIRED", "FULL_OFFLINE requires a local signed bundle id")
        val store = OfflineDeploymentStore(context)
        val state = store.load()
        if (state?.bundleId == bundleId) {
            if (state.stage == OfflineStage.OFFLINE_VERIFIED) {
                if (config.offlineMode == "OFFLINE_THEN_SYNC") {
                    store.save(state.copy(stage = OfflineStage.SYNC_PENDING, syncPending = true))
                    return OfflineEnrollmentOutcome.Complete(OfflineStage.SYNC_PENDING)
                }
                return OfflineEnrollmentOutcome.Complete(OfflineStage.OFFLINE_VERIFIED)
            }
            if (state.stage == OfflineStage.SYNC_PENDING && config.offlineMode == "OFFLINE_THEN_SYNC") {
                return OfflineEnrollmentOutcome.Complete(OfflineStage.SYNC_PENDING)
            }
            if (state.stage == OfflineStage.FAILED) {
                return OfflineEnrollmentOutcome.Failed("OFFLINE_BUNDLE_INVALID", state.lastError ?: "Offline deployment failed")
            }
            return OfflineEnrollmentOutcome.Retryable("OFFLINE_NOT_VERIFIED", "Offline deployment is ${state.stage}; OFFLINE_VERIFIED is required")
        }

        // Factory-reset single-QR provisioning can only continue when a signed bundle has
        // already been materialized into device-protected storage by the build/operator path.
        // Never fall through to online enrollment for FULL_OFFLINE.
        return OfflineEnrollmentOutcome.Failed("OFFLINE_BUNDLE_REQUIRED", "Local offline bundle '$bundleId' is not available")
    }
}
