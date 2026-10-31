package io.dpcaio.engineering

enum class VendorProfileConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class VendorProfileMatchReason {
    EXACT_PACKAGE_EXACT_CERT,
    PACKAGE_PREFIX_EXACT_CERT,
    EXACT_PACKAGE,
    PACKAGE_PREFIX,
}

data class SignatureCodeVendorProfile(
    val id: String,
    val displayName: String,
    val vendor: String,
    val exactPackageNames: Set<String> = emptySet(),
    val packagePrefixes: Set<String> = emptySet(),
    val certificateSha256Fingerprints: Set<String> = emptySet(),
    val certificateSubjectFragments: Set<String> = emptySet(),
    val recipe: SignatureCodeRecipe,
    val expectedOutputKind: DerivedCodeKind,
    val provenance: String,
    val confidence: VendorProfileConfidence,
    val enabled: Boolean = true,
)

data class VendorProfileMatch(
    val profile: SignatureCodeVendorProfile,
    val reason: VendorProfileMatchReason,
    val score: Int,
)
