package io.dpcaio.execution

import io.dpcaio.model.CapabilityEvidence
import io.dpcaio.model.CapabilityState
import io.dpcaio.model.EnterpriseCapability
import io.dpcaio.model.EnterpriseRoute

class EnterpriseCapabilityRouter {
    private val priority = listOf(
        EnterpriseRoute.ANDROID_DPM,
        EnterpriseRoute.KNOX_OFFICIAL,
        EnterpriseRoute.SAMSUNG_SEM,
        EnterpriseRoute.OEM_INTERNAL,
    )

    fun route(capabilityId: String, inventory: List<EnterpriseCapability>): EnterpriseCapability {
        val matching = inventory.filter { it.id == capabilityId }
        for (route in priority) {
            val candidate = matching.firstOrNull { it.route == route && it.state == CapabilityState.AVAILABLE }
            if (candidate != null) return candidate
        }
        val bestEvidence = matching.maxByOrNull { it.evidence.ordinal }
        return bestEvidence?.copy(route = EnterpriseRoute.UNAVAILABLE)
            ?: EnterpriseCapability(
                id = capabilityId,
                route = EnterpriseRoute.UNAVAILABLE,
                state = CapabilityState.UNAVAILABLE,
                evidence = CapabilityEvidence.DISCOVERED_IN_REFERENCE_APK,
                details = "NO_SUPPORTED_ROUTE",
            )
    }
}
