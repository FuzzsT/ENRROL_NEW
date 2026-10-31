package io.dpcaio.engineering

data class SigningCertificateIdentity(
    val derBytes: ByteArray,
    val sha256Hex: String,
    val sha1Hex: String,
    val subjectSummary: String? = null,
    val serialNumber: String? = null,
)

data class AppSigningIdentity(
    val packageName: String,
    val versionCode: Long? = null,
    val currentSigners: List<SigningCertificateIdentity>,
    val signingHistory: List<SigningCertificateIdentity> = emptyList(),
    val hasMultipleSigners: Boolean = currentSigners.size > 1,
)

enum class DerivedCodeKind {
    NUMERIC,
    HEX,
    TEXT,
}

enum class SignatureCodeSource {
    AUTO,
    VENDOR_PROFILE,
    CUSTOM_FORMULA,
    SAMPLE_MATCH,
}

data class DerivationStage(
    val operation: String,
    val representation: String,
)

data class DerivedCodeCandidate(
    val packageName: String,
    val signerFingerprintSha256: String,
    val recipeId: String,
    val recipeName: String,
    val candidate: String,
    val candidateKind: DerivedCodeKind,
    val source: SignatureCodeSource,
    val stages: List<DerivationStage>,
)

data class SignatureCodeLimits(
    val maxPipelineSteps: Int = 16,
    val maxInputBytes: Int = 1024 * 1024,
    val maxSamples: Int = 32,
    val maxRecipesEvaluated: Int = 5_000,
) {
    init {
        require(maxPipelineSteps > 0)
        require(maxInputBytes > 0)
        require(maxSamples > 0)
        require(maxRecipesEvaluated > 0)
    }
}
