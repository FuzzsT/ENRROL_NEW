package io.dpcaio.app

import android.content.Context
import io.dpcaio.knox.license.KnoxRuntimeAccess
import io.dpcaio.knox.license.KnoxStartupDecision

data class PersistedKnoxRuntimeState(
    val decision: KnoxStartupDecision,
    val access: KnoxRuntimeAccess,
    val evaluatedAtEpochMillis: Long
)

object KnoxRuntimeStateStore {
    private const val PREFS = "knox_runtime_state"
    private const val KEY_DECISION = "decision"
    private const val KEY_MDM_GATE = "mdm_gate_active"
    private const val KEY_REAL_ACTIVE = "real_knox_active"
    private const val KEY_LAB_ACTIVE = "lab_simulated_active"
    private const val KEY_DPM = "allow_dpm_package_control"
    private const val KEY_KNOX_ONLY = "allow_knox_only_apis"
    private const val KEY_EVALUATED = "evaluated_at"
    private const val KEY_REAL_STATE = "real_license_state"
    private const val KEY_REAL_KEY = "real_key_configured"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(context: Context, state: PersistedKnoxRuntimeState) {
        prefs(context).edit()
            .putString(KEY_DECISION, state.decision.name)
            .putBoolean(KEY_MDM_GATE, state.access.mdmGateActive)
            .putBoolean(KEY_REAL_ACTIVE, state.access.realKnoxActive)
            .putBoolean(KEY_LAB_ACTIVE, state.access.labSimulatedActive)
            .putBoolean(KEY_DPM, state.access.allowDpmPackageControl)
            .putBoolean(KEY_KNOX_ONLY, state.access.allowKnoxOnlyApis)
            .putLong(KEY_EVALUATED, state.evaluatedAtEpochMillis)
            .apply()
    }

    fun read(context: Context): PersistedKnoxRuntimeState? {
        val p = prefs(context)
        val name = p.getString(KEY_DECISION, null) ?: return null
        val decision = runCatching { KnoxStartupDecision.valueOf(name) }.getOrNull() ?: return null
        return PersistedKnoxRuntimeState(
            decision = decision,
            access = KnoxRuntimeAccess(
                mdmGateActive = p.getBoolean(KEY_MDM_GATE, false),
                realKnoxActive = p.getBoolean(KEY_REAL_ACTIVE, false),
                labSimulatedActive = p.getBoolean(KEY_LAB_ACTIVE, false),
                allowDpmPackageControl = p.getBoolean(KEY_DPM, false),
                allowKnoxOnlyApis = p.getBoolean(KEY_KNOX_ONLY, false)
            ),
            evaluatedAtEpochMillis = p.getLong(KEY_EVALUATED, 0L)
        )
    }

    fun realLicenseStateName(context: Context): String =
        prefs(context).getString(KEY_REAL_STATE, "UNKNOWN") ?: "UNKNOWN"

    fun setRealLicenseStateName(context: Context, state: String) {
        prefs(context).edit().putString(KEY_REAL_STATE, state).apply()
    }

    fun hasRealKeyConfigured(context: Context): Boolean = prefs(context).getBoolean(KEY_REAL_KEY, false)

    fun setRealKeyConfigured(context: Context, configured: Boolean) {
        prefs(context).edit().putBoolean(KEY_REAL_KEY, configured).apply()
    }
}
