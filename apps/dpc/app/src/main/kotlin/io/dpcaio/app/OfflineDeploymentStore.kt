package io.dpcaio.app

import android.content.Context
import io.dpcaio.offline.OfflineStage

data class OfflineDeploymentState(
    val bundleId: String,
    val stage: OfflineStage,
    val parentSessionId: Int? = null,
    val commitSessionId: Int? = null,
    val syncPending: Boolean = false,
    val lastError: String? = null,
    val bundlePath: String? = null
)

class OfflineDeploymentStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("dpc_aio_offline_deployment", Context.MODE_PRIVATE)

    fun save(state: OfflineDeploymentState) {
        prefs.edit()
            .putString("bundleId", state.bundleId)
            .putString("stage", state.stage.name)
            .putInt("parentSessionId", state.parentSessionId ?: -1)
            .putInt("commitSessionId", state.commitSessionId ?: -1)
            .putBoolean("syncPending", state.syncPending)
            .putString("lastError", state.lastError)
            .putString("bundlePath", state.bundlePath)
            .apply()
    }

    fun load(): OfflineDeploymentState? {
        val bundleId = prefs.getString("bundleId", null) ?: return null
        val stage = prefs.getString("stage", null)?.let { runCatching { OfflineStage.valueOf(it) }.getOrNull() } ?: return null
        val session = prefs.getInt("parentSessionId", -1).takeIf { it >= 0 }
        val commitSession = prefs.getInt("commitSessionId", -1).takeIf { it >= 0 }
        return OfflineDeploymentState(
            bundleId = bundleId,
            stage = stage,
            parentSessionId = session,
            commitSessionId = commitSession,
            syncPending = prefs.getBoolean("syncPending", false),
            lastError = prefs.getString("lastError", null),
            bundlePath = prefs.getString("bundlePath", null),
        )
    }

    fun clear() = prefs.edit().clear().apply()
}
