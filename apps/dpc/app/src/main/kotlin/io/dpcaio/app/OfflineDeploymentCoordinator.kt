package io.dpcaio.app

import io.dpcaio.offline.OfflineMode
import io.dpcaio.offline.OfflineStage

data class OfflineDeploymentResult(
    val mode: OfflineMode,
    val stage: OfflineStage,
    val verified: Boolean,
    val detail: String
)

class OfflineDeploymentCoordinator(
    private val store: OfflineDeploymentStore
) {
    fun startFullOffline(bundleId: String): OfflineDeploymentResult {
        val mode = OfflineMode.FULL_OFFLINE
        store.save(OfflineDeploymentState(bundleId, OfflineStage.BUNDLE_RECEIVED))
        return OfflineDeploymentResult(mode, OfflineStage.BUNDLE_RECEIVED, verified = false, detail = "FULL_OFFLINE")
    }

    fun markVerified(bundleId: String): OfflineDeploymentResult {
        store.save(OfflineDeploymentState(bundleId, OfflineStage.OFFLINE_VERIFIED))
        return OfflineDeploymentResult(OfflineMode.FULL_OFFLINE, OfflineStage.OFFLINE_VERIFIED, verified = true, detail = "OFFLINE_VERIFIED")
    }
}
