package io.dpcaio.app

import android.content.Context

/** Stable integration surface for MDM startup and feature gates. */
object KnoxRuntimeGate {
    private fun state(context: Context): PersistedKnoxRuntimeState =
        KnoxRuntimeStateStore.read(context.applicationContext)
            ?: KnoxStartupController.evaluateAndPersist(context.applicationContext)

    fun isMdmGateActive(context: Context): Boolean = state(context).access.mdmGateActive

    fun isRealKnoxActive(context: Context): Boolean = state(context).access.realKnoxActive

    fun isLabSimulatedActive(context: Context): Boolean = state(context).access.labSimulatedActive

    fun canManagePackagesWithDpm(context: Context): Boolean = state(context).access.allowDpmPackageControl

    fun canUseKnoxOnlyApis(context: Context): Boolean = state(context).access.allowKnoxOnlyApis
}
