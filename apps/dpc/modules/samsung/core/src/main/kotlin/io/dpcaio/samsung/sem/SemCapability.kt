package io.dpcaio.samsung.sem

import io.dpcaio.model.CapabilityEvidence
import io.dpcaio.model.CapabilityState
import io.dpcaio.model.EnterpriseCapability
import io.dpcaio.model.EnterpriseRoute

enum class SemProbeStage {
    DISCOVERED_IN_REFERENCE_APK,
    CLASS_PRESENT,
    METHOD_PRESENT,
    PERMISSION_SATISFIED,
    CALL_SUCCEEDED,
    READBACK_VERIFIED,
}

data class SemCapabilitySpec(
    val id: String,
    val className: String,
    val methodName: String,
    val parameterTypeNames: List<String> = emptyList(),
    val readOnly: Boolean = true,
    val samsungOnly: Boolean = true,
    val requiredPermission: String? = null,
)

data class SemProbeResult(
    val spec: SemCapabilitySpec,
    val stage: SemProbeStage,
    val state: CapabilityState,
    val detail: String,
    val valueSummary: String? = null,
) {
    fun asEnterpriseCapability(): EnterpriseCapability = EnterpriseCapability(
        id = spec.id,
        route = EnterpriseRoute.SAMSUNG_SEM,
        state = state,
        evidence = when (stage) {
            SemProbeStage.DISCOVERED_IN_REFERENCE_APK -> CapabilityEvidence.DISCOVERED_IN_REFERENCE_APK
            SemProbeStage.CLASS_PRESENT -> CapabilityEvidence.CLASS_PRESENT
            SemProbeStage.METHOD_PRESENT -> CapabilityEvidence.METHOD_PRESENT
            SemProbeStage.PERMISSION_SATISFIED -> CapabilityEvidence.PERMISSION_SATISFIED
            SemProbeStage.CALL_SUCCEEDED -> CapabilityEvidence.CALL_SUCCEEDED
            SemProbeStage.READBACK_VERIFIED -> CapabilityEvidence.READBACK_VERIFIED
        },
        requiredPermission = spec.requiredPermission,
        details = detail,
    )
}
