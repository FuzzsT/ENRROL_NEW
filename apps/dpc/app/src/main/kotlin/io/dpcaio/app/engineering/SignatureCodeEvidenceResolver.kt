package io.dpcaio.app.engineering

import android.content.Context
import io.dpcaio.engineering.DerivedCodeCandidate
import io.dpcaio.engineering.SignatureCodeSource

enum class SignatureCodeEvidence {
    SIGNATURE_DERIVED,
    VENDOR_PROFILE_MATCH,
    SAMPLE_MATCHED,
    MANIFEST_MATCH,
    RUNTIME_VERIFIED,
}

data class SignatureCodeEvidenceResult(
    val candidate: DerivedCodeCandidate,
    val normalizedCandidate: String,
    val evidence: Set<SignatureCodeEvidence>,
    val inventoryMatches: List<SecretCodeInventoryItem>,
    val runtimeReceivers: List<RuntimeSecretCodeReceiver>,
)

class SignatureCodeEvidenceResolver(context: Context) {
    private val inventoryRepository = SecretCodeInventoryRepository(context)
    private val runtimeVerifier = SecretCodeRuntimeVerifier(context)

    fun resolve(candidate: DerivedCodeCandidate): SignatureCodeEvidenceResult {
        val normalized = SamsungSecretCodeCatalog.normalize(candidate.candidate)
        val evidence = linkedSetOf(SignatureCodeEvidence.SIGNATURE_DERIVED)

        when (candidate.source) {
            SignatureCodeSource.VENDOR_PROFILE -> evidence += SignatureCodeEvidence.VENDOR_PROFILE_MATCH
            SignatureCodeSource.SAMPLE_MATCH -> evidence += SignatureCodeEvidence.SAMPLE_MATCHED
            SignatureCodeSource.AUTO,
            SignatureCodeSource.CUSTOM_FORMULA,
            -> Unit
        }

        if (normalized.isBlank()) {
            return SignatureCodeEvidenceResult(
                candidate = candidate,
                normalizedCandidate = normalized,
                evidence = evidence,
                inventoryMatches = emptyList(),
                runtimeReceivers = emptyList(),
            )
        }

        val inventoryMatches = inventoryRepository.scan()
            .filter { item ->
                item.normalizedCode?.let(SamsungSecretCodeCatalog::normalize) == normalized ||
                    item.rawHost?.let(SamsungSecretCodeCatalog::normalize) == normalized
            }
            .sortedWith(
                compareByDescending<SecretCodeInventoryItem> { it.packageName == candidate.packageName }
                    .thenBy { it.packageName }
                    .thenBy { it.receiverClassName }
            )

        if (inventoryMatches.isNotEmpty()) {
            evidence += SignatureCodeEvidence.MANIFEST_MATCH
        }

        val runtimeReceivers = runtimeVerifier.queryReceivers(normalized)
            .sortedWith(
                compareByDescending<RuntimeSecretCodeReceiver> { it.packageName == candidate.packageName }
                    .thenBy { it.packageName }
                    .thenBy { it.className }
                    .thenBy { it.action }
            )

        if (runtimeReceivers.isNotEmpty()) {
            evidence += SignatureCodeEvidence.RUNTIME_VERIFIED
        }

        return SignatureCodeEvidenceResult(
            candidate = candidate,
            normalizedCandidate = normalized,
            evidence = evidence,
            inventoryMatches = inventoryMatches,
            runtimeReceivers = runtimeReceivers,
        )
    }
}
