package io.dpcaio.installer

enum class InstallSourceConfidence { CONFIRMED, PARTIAL, ADVISORY, UNKNOWN }
enum class RuntimeIntegrityState { VERIFIED, MISMATCH, UNAVAILABLE }
enum class TransportHashState { PRESENT, MISSING }
enum class PackageTrustState { VERIFIED, PARTIAL, MISMATCH, UNAVAILABLE }
enum class PackageTrustIssue {
    PACKAGE_NAME_MISMATCH,
    VERSION_MISMATCH,
    SIGNER_MISMATCH,
    MISSING_SPLIT,
    EXTRA_SPLIT,
    RUNTIME_CHECKSUM_MISMATCH,
    INSTALL_SOURCE_UNCONFIRMED,
}

data class PackageTrustExpectation(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
    val requiredSplits: Set<String> = emptySet(),
    val allowedExtraSplits: Set<String> = emptySet(),
    val runtimeMerkleChecksums: Map<String, String> = emptyMap(),
)

data class PackageTrustObservation(
    val packageName: String,
    val versionCode: Long,
    val currentSignerSha256: Set<String>,
    val signerLineageSha256: Set<String> = emptySet(),
    val hasMultipleSigners: Boolean,
    val installedSplits: Set<String> = emptySet(),
    val installSourceConfidence: InstallSourceConfidence = InstallSourceConfidence.UNKNOWN,
    val installingPackageName: String? = null,
    val initiatingPackageName: String? = null,
    val runtimeMerkleChecksums: Map<String, String> = emptyMap(),
    val transportSha256: Map<String, String> = emptyMap(),
)

data class PackageTrustSnapshot(
    val expectation: PackageTrustExpectation,
    val observation: PackageTrustObservation,
    val signerVerified: Boolean,
    val runtimeIntegrityState: RuntimeIntegrityState,
    val transportHashState: TransportHashState,
    val missingSplits: Set<String>,
    val extraSplits: Set<String>,
    val issues: Set<PackageTrustIssue>,
    val state: PackageTrustState,
) {
    val acceptedForOffline: Boolean
        get() = signerVerified && issues.none { it in setOf(
            PackageTrustIssue.PACKAGE_NAME_MISMATCH,
            PackageTrustIssue.VERSION_MISMATCH,
            PackageTrustIssue.SIGNER_MISMATCH,
            PackageTrustIssue.MISSING_SPLIT,
            PackageTrustIssue.EXTRA_SPLIT,
            PackageTrustIssue.RUNTIME_CHECKSUM_MISMATCH,
        ) }
}

class PackageTrustPlanner {
    fun evaluate(expectation: PackageTrustExpectation, observation: PackageTrustObservation): PackageTrustSnapshot {
        val issues = linkedSetOf<PackageTrustIssue>()
        if (expectation.packageName != observation.packageName) issues += PackageTrustIssue.PACKAGE_NAME_MISMATCH
        if (expectation.versionCode != observation.versionCode) issues += PackageTrustIssue.VERSION_MISMATCH

        val expectedSigners = expectation.signerSha256.normalized()
        val currentSigners = observation.currentSignerSha256.normalized()
        val lineage = observation.signerLineageSha256.normalized() + currentSigners
        val signerVerified = if (observation.hasMultipleSigners) {
            currentSigners == expectedSigners
        } else {
            expectedSigners.isNotEmpty() && expectedSigners.all { it in lineage }
        }
        if (!signerVerified) issues += PackageTrustIssue.SIGNER_MISMATCH

        val missing = expectation.requiredSplits - observation.installedSplits
        val allowed = expectation.requiredSplits + expectation.allowedExtraSplits
        val extra = observation.installedSplits - allowed
        if (missing.isNotEmpty()) issues += PackageTrustIssue.MISSING_SPLIT
        if (extra.isNotEmpty()) issues += PackageTrustIssue.EXTRA_SPLIT

        val runtimeIntegrityState = when {
            expectation.runtimeMerkleChecksums.isEmpty() && observation.runtimeMerkleChecksums.isEmpty() -> RuntimeIntegrityState.UNAVAILABLE
            expectation.runtimeMerkleChecksums.isEmpty() && observation.runtimeMerkleChecksums.isNotEmpty() -> RuntimeIntegrityState.VERIFIED
            expectation.runtimeMerkleChecksums.all { (name, expected) -> observation.runtimeMerkleChecksums[name]?.equals(expected, true) == true } -> RuntimeIntegrityState.VERIFIED
            else -> RuntimeIntegrityState.MISMATCH
        }
        if (runtimeIntegrityState == RuntimeIntegrityState.MISMATCH) issues += PackageTrustIssue.RUNTIME_CHECKSUM_MISMATCH

        if (observation.installSourceConfidence == InstallSourceConfidence.UNKNOWN) {
            issues += PackageTrustIssue.INSTALL_SOURCE_UNCONFIRMED
        }
        val transportState = if (observation.transportSha256.isEmpty()) TransportHashState.MISSING else TransportHashState.PRESENT

        val hardMismatch = issues.any {
            it in setOf(
                PackageTrustIssue.PACKAGE_NAME_MISMATCH,
                PackageTrustIssue.VERSION_MISMATCH,
                PackageTrustIssue.SIGNER_MISMATCH,
                PackageTrustIssue.MISSING_SPLIT,
                PackageTrustIssue.EXTRA_SPLIT,
                PackageTrustIssue.RUNTIME_CHECKSUM_MISMATCH,
            )
        }
        val state = when {
            hardMismatch -> PackageTrustState.MISMATCH
            observation.installSourceConfidence in setOf(InstallSourceConfidence.UNKNOWN, InstallSourceConfidence.ADVISORY) || runtimeIntegrityState == RuntimeIntegrityState.UNAVAILABLE -> PackageTrustState.PARTIAL
            else -> PackageTrustState.VERIFIED
        }
        return PackageTrustSnapshot(expectation, observation, signerVerified, runtimeIntegrityState, transportState, missing, extra, issues, state)
    }

    private fun Set<String>.normalized(): Set<String> = mapTo(linkedSetOf()) { it.replace(":", "").trim().uppercase() }
}
