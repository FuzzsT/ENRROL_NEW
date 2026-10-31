package io.dpcaio.engineering

object AutoSignatureCodeCatalog {
    fun recipes(): List<SignatureCodeRecipe> = listOf(
        SignatureCodeRecipe(
            id = "sha256-cert-first4-six",
            displayName = "SHA-256 cert first 4 bytes → 6 digits",
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
        ),
        SignatureCodeRecipe(
            id = "sha256-cert-last4-six",
            displayName = "SHA-256 cert last 4 bytes → 6 digits",
            input = SignatureCodeInputKind.CERT_DER,
            steps = listOf(
                SignatureCodeStep.Sha256,
                SignatureCodeStep.SuffixBytes(4),
                SignatureCodeStep.UIntBe,
                SignatureCodeStep.Mod(1_000_000uL),
                SignatureCodeStep.Decimal,
                SignatureCodeStep.PadLeft(6),
            ),
            outputKind = DerivedCodeKind.NUMERIC,
        ),
        SignatureCodeRecipe(
            id = "sha1-cert-first4-six",
            displayName = "SHA-1 cert first 4 bytes → 6 digits",
            input = SignatureCodeInputKind.CERT_DER,
            steps = listOf(
                SignatureCodeStep.Sha1,
                SignatureCodeStep.PrefixBytes(4),
                SignatureCodeStep.UIntBe,
                SignatureCodeStep.Mod(1_000_000uL),
                SignatureCodeStep.Decimal,
                SignatureCodeStep.PadLeft(6),
            ),
            outputKind = DerivedCodeKind.NUMERIC,
        ),
        SignatureCodeRecipe(
            id = "sha256-package-cert-last8-eight",
            displayName = "SHA-256 package + cert last 8 bytes → 8 digits",
            input = SignatureCodeInputKind.PACKAGE_THEN_CERT_DER,
            steps = listOf(
                SignatureCodeStep.Sha256,
                SignatureCodeStep.SuffixBytes(8),
                SignatureCodeStep.UIntBe,
                SignatureCodeStep.Mod(100_000_000uL),
                SignatureCodeStep.Decimal,
                SignatureCodeStep.PadLeft(8),
            ),
            outputKind = DerivedCodeKind.NUMERIC,
        ),
        SignatureCodeRecipe(
            id = "crc32-cert-eight",
            displayName = "CRC32 cert → 8 digits",
            input = SignatureCodeInputKind.CERT_DER,
            steps = listOf(
                SignatureCodeStep.Crc32,
                SignatureCodeStep.Mod(100_000_000uL),
                SignatureCodeStep.Decimal,
                SignatureCodeStep.PadLeft(8),
            ),
            outputKind = DerivedCodeKind.NUMERIC,
        ),
        SignatureCodeRecipe(
            id = "xor8-cert-decimal",
            displayName = "XOR8 cert → decimal",
            input = SignatureCodeInputKind.CERT_DER,
            steps = listOf(
                SignatureCodeStep.Xor8,
                SignatureCodeStep.Decimal,
            ),
            outputKind = DerivedCodeKind.NUMERIC,
        ),
    )
}
