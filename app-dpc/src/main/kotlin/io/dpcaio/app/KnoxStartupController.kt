package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.dpcaio.knox.license.KnoxBuildTrack
import io.dpcaio.knox.license.KnoxLicenseState
import io.dpcaio.knox.license.KnoxRuntimeAccessPolicy
import io.dpcaio.knox.license.KnoxStartupDecision
import io.dpcaio.knox.license.KnoxStartupGate
import io.dpcaio.knox.license.KnoxStartupInput

object KnoxStartupController {
    const val ACTION_STATE_CHANGED = "io.dpcaio.action.KNOX_RUNTIME_STATE_CHANGED"
    const val EXTRA_DECISION = "decision"
    const val EXTRA_MDM_GATE_ACTIVE = "mdm_gate_active"
    const val EXTRA_REAL_KNOX_ACTIVE = "real_knox_active"
    const val EXTRA_LAB_SIMULATED_ACTIVE = "lab_simulated_active"
    fun evaluateAndPersist(context: Context): PersistedKnoxRuntimeState {
        val app = context.applicationContext
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        val track = buildTrack(BuildConfig.FLAVOR)
        val realState = runCatching {
            KnoxLicenseState.valueOf(KnoxRuntimeStateStore.realLicenseStateName(app))
        }.getOrDefault(KnoxLicenseState.UNKNOWN)

        val decision = KnoxStartupGate().evaluate(
            KnoxStartupInput(
                isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
                isDeviceOwner = dpm.isDeviceOwnerApp(app.packageName),
                buildTrack = track,
                realLicenseState = realState,
                labTokenValid = KnoxFlavorLicenseProvider.isLabSimulatedActive(app),
                networkAvailable = networkAvailable(app),
                hasRealKeyConfigured = KnoxRuntimeStateStore.hasRealKeyConfigured(app)
            )
        )
        val access = KnoxRuntimeAccessPolicy().fromDecision(decision)
        if (decision == KnoxStartupDecision.ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK) {
            check(access.labSimulatedActive && access.allowDpmPackageControl)
        }
        val state = PersistedKnoxRuntimeState(
            decision = decision,
            access = access,
            evaluatedAtEpochMillis = System.currentTimeMillis()
        )
        KnoxRuntimeStateStore.write(app, state)
        app.sendBroadcast(android.content.Intent(ACTION_STATE_CHANGED).apply {
            setPackage(app.packageName)
            putExtra(EXTRA_DECISION, state.decision.name)
            putExtra(EXTRA_MDM_GATE_ACTIVE, state.access.mdmGateActive)
            putExtra(EXTRA_REAL_KNOX_ACTIVE, state.access.realKnoxActive)
            putExtra(EXTRA_LAB_SIMULATED_ACTIVE, state.access.labSimulatedActive)
        })
        return state
    }

    private fun buildTrack(flavor: String): KnoxBuildTrack = when (flavor.lowercase()) {
        "lab" -> KnoxBuildTrack.LAB
        "tst" -> KnoxBuildTrack.TST
        "eng" -> KnoxBuildTrack.ENG
        "systemprivileged" -> KnoxBuildTrack.SYSTEM_PRIVILEGED
        else -> KnoxBuildTrack.ENTERPRISE
    }

    private fun networkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

}
