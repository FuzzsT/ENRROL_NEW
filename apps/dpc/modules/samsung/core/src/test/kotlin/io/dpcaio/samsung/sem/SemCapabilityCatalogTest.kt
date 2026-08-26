package io.dpcaio.samsung.sem

import io.dpcaio.model.CapabilityEvidence
import io.dpcaio.model.CapabilityState

fun main() {
    check(SemCapabilityCatalog.entries.isNotEmpty())
    check(SemCapabilityCatalog.entries.all { it.readOnly })
    check(SemCapabilityCatalog.entries.map { it.id }.toSet().size == SemCapabilityCatalog.entries.size)

    val spec = SemCapabilityCatalog.entries.first()
    val methodPresent = SemProbeResult(spec, SemProbeStage.METHOD_PRESENT, CapabilityState.READ_ONLY, "METHOD_PRESENT")
        .asEnterpriseCapability()
    check(methodPresent.evidence == CapabilityEvidence.METHOD_PRESENT)
    check(!methodPresent.operational)

    val verified = SemProbeResult(spec, SemProbeStage.READBACK_VERIFIED, CapabilityState.AVAILABLE, "READBACK_VERIFIED")
        .asEnterpriseCapability()
    check(verified.operational)
    println("SemCapabilityCatalogTest: PASS")
}
