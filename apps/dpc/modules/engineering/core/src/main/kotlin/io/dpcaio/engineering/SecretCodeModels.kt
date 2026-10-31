package io.dpcaio.engineering

enum class SecretCodeSourceKind {
    BASE_APK,
    SPLIT_APK,
}

enum class SecretCodeDeclarationKind {
    LITERAL_HOST,
    PATTERN_ONLY,
}

enum class SecretCodeExtractionError {
    MALFORMED_AXML,
    LIMIT_EXCEEDED,
    APK_NOT_READABLE,
    MANIFEST_MISSING,
}

data class SecretCodeDeclaration(
    val rawHost: String?,
    val normalizedCode: String?,
    val receiverClassName: String,
    val actions: Set<String>,
    val scheme: String?,
    val enabled: Boolean?,
    val exported: Boolean?,
    val requiredPermission: String?,
    val sourceKind: SecretCodeSourceKind,
    val sourceApkName: String,
    val declarationKind: SecretCodeDeclarationKind,
)

data class SecretCodeExtractionLimits(
    val maxManifestBytes: Int = 2 * 1024 * 1024,
    val maxStringPoolBytes: Int = 1024 * 1024,
    val maxNodes: Int = 20_000,
    val maxCandidates: Int = 2_000,
) {
    init {
        require(maxManifestBytes > 0)
        require(maxStringPoolBytes > 0)
        require(maxNodes > 0)
        require(maxCandidates >= 0)
    }
}

sealed interface SecretCodeExtractionResult {
    data class Success(val declarations: List<SecretCodeDeclaration>) : SecretCodeExtractionResult
    data class Failure(
        val error: SecretCodeExtractionError,
        val message: String,
    ) : SecretCodeExtractionResult
}
