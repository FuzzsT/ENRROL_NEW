package io.dpcaio.engineering

private fun vendorRecipe(id: String = "vendor-six") = SignatureCodeRecipe(
    id = id,
    displayName = id,
    input = SignatureCodeInputKind.CERT_DER,
    steps = listOf(
        SignatureCodeStep.Sha256,
        SignatureCodeStep.PrefixBytes(4),
        SignatureCodeStep.UIntBe,
        SignatureCodeStep.Mod(1_000_000uL),
        SignatureCodeStep.Decimal,
        SignatureCodeStep.PadLeft(6),
    ),
    outputKind = DerivedCodeKind.NUMERIC,
)

private fun profile(
    id: String,
    exactPackageNames: Set<String> = emptySet(),
    packagePrefixes: Set<String> = emptySet(),
    certificateSha256Fingerprints: Set<String> = emptySet(),
    certificateSubjectFragments: Set<String> = emptySet(),
    recipe: SignatureCodeRecipe = vendorRecipe(id),
    enabled: Boolean = true,
) = SignatureCodeVendorProfile(
    id = id,
    displayName = id,
    vendor = "FixtureVendor",
    exactPackageNames = exactPackageNames,
    packagePrefixes = packagePrefixes,
    certificateSha256Fingerprints = certificateSha256Fingerprints,
    certificateSubjectFragments = certificateSubjectFragments,
    recipe = recipe,
    expectedOutputKind = DerivedCodeKind.NUMERIC,
    provenance = "fixture-test",
    confidence = VendorProfileConfidence.HIGH,
    enabled = enabled,
)

fun main() {
    val signer = SigningCertificateIdentity(
        derBytes = byteArrayOf(1, 2, 3, 4),
        sha256Hex = "AA11BB22CC33DD44",
        sha1Hex = "fixture",
        subjectSummary = "CN=FixtureVendor Signing",
    )
    val identity = AppSigningIdentity(
        packageName = "com.fixture.service",
        currentSigners = listOf(signer),
    )

    val profiles = listOf(
        profile(
            id = "prefix-only",
            packagePrefixes = setOf("com.fixture."),
        ),
        profile(
            id = "exact-package",
            exactPackageNames = setOf("com.fixture.service"),
        ),
        profile(
            id = "prefix-cert",
            packagePrefixes = setOf("com.fixture."),
            certificateSha256Fingerprints = setOf("aa11bb22cc33dd44"),
        ),
        profile(
            id = "exact-cert",
            exactPackageNames = setOf("com.fixture.service"),
            certificateSha256Fingerprints = setOf("AA11BB22CC33DD44"),
        ),
        profile(
            id = "subject-only",
            certificateSubjectFragments = setOf("FixtureVendor"),
        ),
        profile(
            id = "cert-only",
            certificateSha256Fingerprints = setOf("AA11BB22CC33DD44"),
        ),
    )

    val matches = SignatureCodeVendorProfileMatcher.match(identity, profiles)
    check(matches.map { it.profile.id } == listOf("exact-cert", "prefix-cert", "exact-package", "prefix-only")) {
        "profile precedence must be exact+cert > prefix+cert > exact package > prefix: $matches"
    }
    check(matches.map { it.reason } == listOf(
        VendorProfileMatchReason.EXACT_PACKAGE_EXACT_CERT,
        VendorProfileMatchReason.PACKAGE_PREFIX_EXACT_CERT,
        VendorProfileMatchReason.EXACT_PACKAGE,
        VendorProfileMatchReason.PACKAGE_PREFIX,
    ))
    check(matches.none { it.profile.id == "subject-only" || it.profile.id == "cert-only" }) {
        "subject-only and cert-only profiles must not activate"
    }

    val subjectSupplement = profile(
        id = "subject-supplement",
        exactPackageNames = setOf("com.fixture.service"),
        certificateSubjectFragments = setOf("FixtureVendor"),
    )
    val subjectMatch = SignatureCodeVendorProfileMatcher.match(identity, listOf(subjectSupplement)).single()
    check(subjectMatch.reason == VendorProfileMatchReason.EXACT_PACKAGE) {
        "subject fragment is supplementary only and must not create a stronger match reason"
    }

    val badRecipe = vendorRecipe("bad").copy(
        steps = listOf(SignatureCodeStep.UIntBe, SignatureCodeStep.Mod(0uL)),
    )
    val invalid = profile(
        id = "invalid-recipe",
        exactPackageNames = setOf("com.fixture.service"),
        recipe = badRecipe,
    )
    val valid = profile(
        id = "valid-profile",
        exactPackageNames = setOf("com.fixture.service"),
    )
    val isolated = SignatureCodeVendorProfileMatcher.match(identity, listOf(invalid, valid))
    check(isolated.map { it.profile.id } == listOf("valid-profile")) {
        "invalid profile recipe must be isolated without dropping valid profiles: $isolated"
    }

    val disabled = profile(
        id = "disabled-profile",
        exactPackageNames = setOf("com.fixture.service"),
        enabled = false,
    )
    check(SignatureCodeVendorProfileMatcher.match(identity, listOf(disabled)).isEmpty()) {
        "disabled profiles must not activate"
    }

    println("SignatureCodeVendorProfileMatcherTest: PASS")
}
