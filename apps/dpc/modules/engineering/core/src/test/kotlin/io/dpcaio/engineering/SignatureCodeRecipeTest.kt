package io.dpcaio.engineering

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val signer = SigningCertificateIdentity(
        derBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67),
        sha256Hex = "0123456789ABCDEF",
        sha1Hex = "89ABCDEF01234567",
        subjectSummary = "CN=Fixture",
        serialNumber = "01",
    )
    val identity = AppSigningIdentity(
        packageName = "com.example.fixture",
        versionCode = 42L,
        currentSigners = listOf(signer),
    )
    assertEquals("com.example.fixture", identity.packageName, "package name must be preserved")
    assertTrue(!identity.hasMultipleSigners, "single signer must not report multiple signers")

    val validRecipe = SignatureCodeRecipe(
        id = "fixture-six-digit",
        displayName = "Fixture six digit",
        input = SignatureCodeInputKind.CERT_DER,
        steps = listOf(
            SignatureCodeStep.Sha256,
            SignatureCodeStep.PrefixBytes(4),
            SignatureCodeStep.UIntBe,
            SignatureCodeStep.Mod(1_000_000uL),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(width = 6, pad = '0'),
        ),
        outputKind = DerivedCodeKind.NUMERIC,
    )
    assertEquals(
        SignatureCodeRecipeValidation.Valid,
        SignatureCodeRecipeValidator.validate(validRecipe),
        "approved typed pipeline must validate",
    )

    val textRecipe = SignatureCodeRecipe(
        id = "package-prefix",
        displayName = "Package prefix",
        input = SignatureCodeInputKind.PACKAGE_NAME_UTF8,
        steps = listOf(
            SignatureCodeStep.Ascii,
            SignatureCodeStep.PrefixChars(3),
        ),
        outputKind = DerivedCodeKind.TEXT,
    )
    assertEquals(
        SignatureCodeRecipeValidation.Valid,
        SignatureCodeRecipeValidator.validate(textRecipe),
        "UTF-8 package bytes must require an explicit ASCII/text conversion before char slicing",
    )

    val tooLong = validRecipe.copy(
        steps = List(17) { SignatureCodeStep.Sha256 },
    )
    val tooLongResult = SignatureCodeRecipeValidator.validate(tooLong)
    assertTrue(
        tooLongResult is SignatureCodeRecipeValidation.Invalid &&
            tooLongResult.error == SignatureCodeRecipeError.PIPELINE_TOO_LONG,
        "pipeline > 16 steps must be rejected",
    )

    val badMod = validRecipe.copy(
        steps = listOf(SignatureCodeStep.UIntBe, SignatureCodeStep.Mod(0uL)),
    )
    val badModResult = SignatureCodeRecipeValidator.validate(badMod)
    assertTrue(
        badModResult is SignatureCodeRecipeValidation.Invalid &&
            badModResult.error == SignatureCodeRecipeError.INVALID_MODULUS,
        "MOD(0) must be rejected",
    )

    val badSlice = validRecipe.copy(
        steps = listOf(SignatureCodeStep.SliceBytes(offset = -1, length = 2)),
    )
    val badSliceResult = SignatureCodeRecipeValidator.validate(badSlice)
    assertTrue(
        badSliceResult is SignatureCodeRecipeValidation.Invalid &&
            badSliceResult.error == SignatureCodeRecipeError.SLICE_OUT_OF_RANGE,
        "negative slice offset must be rejected",
    )

    val typeMismatch = validRecipe.copy(
        steps = listOf(SignatureCodeStep.HexUpper, SignatureCodeStep.UIntBe),
    )
    val typeMismatchResult = SignatureCodeRecipeValidator.validate(typeMismatch)
    assertTrue(
        typeMismatchResult is SignatureCodeRecipeValidation.Invalid &&
            typeMismatchResult.error == SignatureCodeRecipeError.TYPE_MISMATCH,
        "text -> UIntBe without an explicit bytes conversion must be rejected",
    )

    val outputMismatch = SignatureCodeRecipe(
        id = "bytes-claimed-numeric",
        displayName = "Bytes incorrectly claimed numeric",
        input = SignatureCodeInputKind.CERT_DER,
        steps = listOf(SignatureCodeStep.Sha256),
        outputKind = DerivedCodeKind.NUMERIC,
    )
    val outputMismatchResult = SignatureCodeRecipeValidator.validate(outputMismatch)
    assertTrue(
        outputMismatchResult is SignatureCodeRecipeValidation.Invalid &&
            outputMismatchResult.error == SignatureCodeRecipeError.OUTPUT_KIND_MISMATCH,
        "raw byte output must not be labelled NUMERIC",
    )

    val candidate = DerivedCodeCandidate(
        packageName = identity.packageName,
        signerFingerprintSha256 = signer.sha256Hex,
        recipeId = validRecipe.id,
        recipeName = validRecipe.displayName,
        candidate = "123456",
        candidateKind = DerivedCodeKind.NUMERIC,
        source = SignatureCodeSource.AUTO,
        stages = listOf(DerivationStage("SHA256", "bytes")),
    )
    assertEquals("fixture-six-digit", candidate.recipeId, "candidate must retain recipe provenance")
    assertEquals(SignatureCodeSource.AUTO, candidate.source, "candidate must retain derivation source")
    assertEquals(DerivedCodeKind.NUMERIC, candidate.candidateKind, "candidate kind must be explicit")

    println("SignatureCodeRecipeTest: PASS")
}
