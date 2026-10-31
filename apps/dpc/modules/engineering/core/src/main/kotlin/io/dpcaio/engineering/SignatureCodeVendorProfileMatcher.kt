package io.dpcaio.engineering

object SignatureCodeVendorProfileMatcher {
    fun match(
        identity: AppSigningIdentity,
        profiles: List<SignatureCodeVendorProfile>,
    ): List<VendorProfileMatch> {
        val signerFingerprints = identity.currentSigners
            .map { normalizeFingerprint(it.sha256Hex) }
            .filter { it.isNotEmpty() }
            .toSet()

        return profiles.mapNotNull { profile ->
            if (!profile.enabled) return@mapNotNull null
            if (profile.expectedOutputKind != profile.recipe.outputKind) return@mapNotNull null
            if (SignatureCodeRecipeValidator.validate(profile.recipe) !is SignatureCodeRecipeValidation.Valid) {
                return@mapNotNull null
            }

            val exactPackage = identity.packageName in profile.exactPackageNames
            val prefixPackage = profile.packagePrefixes.any { prefix ->
                prefix.isNotEmpty() && identity.packageName.startsWith(prefix)
            }
            if (!exactPackage && !prefixPackage) return@mapNotNull null

            val pinnedFingerprints = profile.certificateSha256Fingerprints
                .map(::normalizeFingerprint)
                .filter { it.isNotEmpty() }
                .toSet()
            val certificatePinned = pinnedFingerprints.isNotEmpty()
            val exactCertificate = certificatePinned && signerFingerprints.any { it in pinnedFingerprints }

            if (certificatePinned && !exactCertificate) return@mapNotNull null

            val reason = when {
                exactPackage && exactCertificate -> VendorProfileMatchReason.EXACT_PACKAGE_EXACT_CERT
                prefixPackage && exactCertificate -> VendorProfileMatchReason.PACKAGE_PREFIX_EXACT_CERT
                exactPackage -> VendorProfileMatchReason.EXACT_PACKAGE
                else -> VendorProfileMatchReason.PACKAGE_PREFIX
            }
            VendorProfileMatch(
                profile = profile,
                reason = reason,
                score = score(reason),
            )
        }.sortedWith(
            compareByDescending<VendorProfileMatch> { it.score }
                .thenBy { it.profile.id }
        )
    }

    private fun normalizeFingerprint(value: String): String = buildString(value.length) {
        value.forEach { char ->
            if (char.isLetterOrDigit()) append(char.uppercaseChar())
        }
    }

    private fun score(reason: VendorProfileMatchReason): Int = when (reason) {
        VendorProfileMatchReason.EXACT_PACKAGE_EXACT_CERT -> 400
        VendorProfileMatchReason.PACKAGE_PREFIX_EXACT_CERT -> 300
        VendorProfileMatchReason.EXACT_PACKAGE -> 200
        VendorProfileMatchReason.PACKAGE_PREFIX -> 100
    }
}
