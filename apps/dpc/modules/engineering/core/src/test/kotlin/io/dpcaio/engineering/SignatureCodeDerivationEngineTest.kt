package io.dpcaio.engineering

private fun expectCandidate(
    result: SignatureCodeDerivationResult,
    expected: String,
    label: String,
): DerivedCodeCandidate {
    val success = result as? SignatureCodeDerivationResult.Success
        ?: error("$label: expected success, got $result")
    check(success.candidate.candidate == expected) {
        "$label: expected=$expected actual=${success.candidate.candidate}"
    }
    return success.candidate
}

private fun recipe(
    id: String,
    input: SignatureCodeInputKind = SignatureCodeInputKind.CERT_DER,
    kind: DerivedCodeKind = DerivedCodeKind.NUMERIC,
    vararg steps: SignatureCodeStep,
) = SignatureCodeRecipe(
    id = id,
    displayName = id,
    input = input,
    steps = steps.toList(),
    outputKind = kind,
)

fun main() {
    val signer = SigningCertificateIdentity(
        derBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67),
        sha256Hex = "E314EC0E5963F2F9F74FF4E884CF1F09ABAF4FDE9024F4CD4A47807F8DA9F096",
        sha1Hex = "8CD28FC05E2AC7727D38F47D23300634DC376B3D",
    )
    val identity = AppSigningIdentity(
        packageName = "com.example.fixture",
        versionCode = 42,
        currentSigners = listOf(signer),
    )
    val engine = SignatureCodeDerivationEngine()

    val sixDigit = recipe(
        "sha256-first4-six",
        steps = arrayOf(
            SignatureCodeStep.Sha256,
            SignatureCodeStep.PrefixBytes(4),
            SignatureCodeStep.UIntBe,
            SignatureCodeStep.Mod(1_000_000uL),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(6),
        ),
    )
    val first = expectCandidate(engine.derive(identity, sixDigit, signer), "799182", "sha256 first4")
    val second = expectCandidate(engine.derive(identity, sixDigit, signer), "799182", "deterministic repeat")
    check(first.stages == second.stages) { "stage list must be deterministic" }
    check(first.stages.isNotEmpty()) { "stage provenance must be present" }

    val sha1Hex = recipe(
        "sha1-hex",
        kind = DerivedCodeKind.HEX,
        steps = arrayOf(SignatureCodeStep.Sha1, SignatureCodeStep.HexUpper),
    )
    expectCandidate(
        engine.derive(identity, sha1Hex, signer),
        "8CD28FC05E2AC7727D38F47D23300634DC376B3D",
        "sha1 exact",
    )

    val packagePrefix = recipe(
        "package-prefix",
        input = SignatureCodeInputKind.PACKAGE_NAME_UTF8,
        kind = DerivedCodeKind.TEXT,
        steps = arrayOf(SignatureCodeStep.Ascii, SignatureCodeStep.PrefixChars(3)),
    )
    expectCandidate(engine.derive(identity, packagePrefix, signer), "com", "ASCII package input")

    val packageCert = recipe(
        "package-cert-last8",
        input = SignatureCodeInputKind.PACKAGE_THEN_CERT_DER,
        steps = arrayOf(
            SignatureCodeStep.Sha256,
            SignatureCodeStep.SuffixBytes(8),
            SignatureCodeStep.UIntBe,
            SignatureCodeStep.Mod(100_000_000uL),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(8),
        ),
    )
    expectCandidate(engine.derive(identity, packageCert, signer), "49918566", "package plus cert")

    val crc32 = recipe(
        "crc32",
        steps = arrayOf(
            SignatureCodeStep.Crc32,
            SignatureCodeStep.Mod(100_000_000uL),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(8),
        ),
    )
    expectCandidate(engine.derive(identity, crc32, signer), "47457659", "CRC32")

    val crc8 = recipe(
        "crc8",
        steps = arrayOf(SignatureCodeStep.Crc8, SignatureCodeStep.Decimal),
    )
    expectCandidate(engine.derive(identity, crc8, signer), "192", "CRC8/SMBUS")

    val crc16 = recipe(
        "crc16",
        steps = arrayOf(SignatureCodeStep.Crc16, SignatureCodeStep.Decimal),
    )
    expectCandidate(engine.derive(identity, crc16, signer), "50074", "CRC16/CCITT-FALSE")

    val xor = recipe(
        "xor8",
        steps = arrayOf(SignatureCodeStep.Xor8, SignatureCodeStep.Decimal),
    )
    expectCandidate(engine.derive(identity, xor, signer), "0", "XOR8")

    val sum8 = recipe(
        "sum8",
        steps = arrayOf(SignatureCodeStep.Sum8, SignatureCodeStep.Decimal),
    )
    expectCandidate(engine.derive(identity, sum8, signer), "208", "SUM8")

    val sum16 = recipe(
        "sum16",
        steps = arrayOf(SignatureCodeStep.Sum16, SignatureCodeStep.Decimal),
    )
    expectCandidate(engine.derive(identity, sum16, signer), "208", "SUM16")

    val luhnSigner = signer.copy(sha256Hex = "7992739871")
    val luhnIdentity = identity.copy(currentSigners = listOf(luhnSigner))
    val luhnAppend = recipe(
        "luhn-append",
        input = SignatureCodeInputKind.CERT_SHA256_HEX,
        kind = DerivedCodeKind.TEXT,
        steps = arrayOf(SignatureCodeStep.Ascii, SignatureCodeStep.LuhnAppend),
    )
    expectCandidate(engine.derive(luhnIdentity, luhnAppend, luhnSigner), "79927398713", "Luhn append")

    val luhnValidateSigner = signer.copy(sha256Hex = "79927398713")
    val luhnValidateIdentity = identity.copy(currentSigners = listOf(luhnValidateSigner))
    val luhnValidate = recipe(
        "luhn-validate",
        input = SignatureCodeInputKind.CERT_SHA256_HEX,
        kind = DerivedCodeKind.TEXT,
        steps = arrayOf(SignatureCodeStep.Ascii, SignatureCodeStep.LuhnValidate),
    )
    expectCandidate(engine.derive(luhnValidateIdentity, luhnValidate, luhnValidateSigner), "79927398713", "Luhn validate")

    val badLuhnSigner = signer.copy(sha256Hex = "79927398710")
    val badLuhn = engine.derive(
        identity.copy(currentSigners = listOf(badLuhnSigner)),
        luhnValidate,
        badLuhnSigner,
    )
    check(badLuhn is SignatureCodeDerivationResult.Failure && badLuhn.error == SignatureCodeDerivationError.LUHN_INVALID) {
        "invalid Luhn input must fail closed"
    }

    val oversized = signer.copy(derBytes = ByteArray(9) { it.toByte() })
    val numericOverflow = recipe(
        "overflow",
        steps = arrayOf(SignatureCodeStep.UIntBe, SignatureCodeStep.Decimal),
    )
    val overflowResult = engine.derive(
        identity.copy(currentSigners = listOf(oversized)),
        numericOverflow,
        oversized,
    )
    check(overflowResult is SignatureCodeDerivationResult.Failure && overflowResult.error == SignatureCodeDerivationError.NUMERIC_OVERFLOW) {
        "UInt conversion beyond 8 bytes must fail closed"
    }

    val tinyLimitResult = engine.derive(
        identity,
        sixDigit,
        signer,
        SignatureCodeLimits(maxInputBytes = 2),
    )
    check(tinyLimitResult is SignatureCodeDerivationResult.Failure && tinyLimitResult.error == SignatureCodeDerivationError.INPUT_TOO_LARGE) {
        "input size limit must be enforced"
    }

    val otherSigner = signer.copy(
        derBytes = byteArrayOf(0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()),
        sha256Hex = "B43F92A05D536F1F50C7A57413D93DEB0B91D7F756F858B78AE734F4114779C3",
    )
    val multi = identity.copy(currentSigners = listOf(signer, otherSigner))
    val other = expectCandidate(engine.derive(multi, sixDigit, otherSigner), "065184", "second signer")
    check(other.candidate != first.candidate) { "current signers must be independently derivable" }

    val auto = AutoSignatureCodeCatalog.recipes()
    check(auto.isNotEmpty()) { "auto catalog must not be empty" }
    check(auto.size <= 8) { "auto catalog must remain intentionally small" }
    check(auto.map { it.id }.distinct().size == auto.size) { "auto recipe IDs must be unique" }

    println("SignatureCodeDerivationEngineTest: PASS")
}
