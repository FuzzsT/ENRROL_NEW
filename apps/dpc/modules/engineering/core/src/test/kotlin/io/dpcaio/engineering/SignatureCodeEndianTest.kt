package io.dpcaio.engineering

private fun expectEndianCandidate(result: SignatureCodeDerivationResult, expected: String, label: String) {
    val success = result as? SignatureCodeDerivationResult.Success
        ?: error("$label: expected success, got $result")
    check(success.candidate.candidate == expected) {
        "$label: expected=$expected actual=${success.candidate.candidate}"
    }
}

fun main() {
    val signer = SigningCertificateIdentity(
        derBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67),
        sha256Hex = "fixture",
        sha1Hex = "fixture",
    )
    val identity = AppSigningIdentity(
        packageName = "com.example.endian",
        currentSigners = listOf(signer),
    )
    val engine = SignatureCodeDerivationEngine()

    val bigEndian = SignatureCodeRecipe(
        id = "uint-be",
        displayName = "UINT BE",
        input = SignatureCodeInputKind.CERT_DER,
        steps = listOf(SignatureCodeStep.UIntBe, SignatureCodeStep.Decimal),
        outputKind = DerivedCodeKind.NUMERIC,
    )
    expectEndianCandidate(engine.derive(identity, bigEndian, signer), "19088743", "UINT_BE")

    val littleEndian = SignatureCodeRecipe(
        id = "uint-le",
        displayName = "UINT LE",
        input = SignatureCodeInputKind.CERT_DER,
        steps = listOf(SignatureCodeStep.UIntLe, SignatureCodeStep.Decimal),
        outputKind = DerivedCodeKind.NUMERIC,
    )
    expectEndianCandidate(engine.derive(identity, littleEndian, signer), "1732584193", "UINT_LE")

    println("SignatureCodeEndianTest: PASS")
}
