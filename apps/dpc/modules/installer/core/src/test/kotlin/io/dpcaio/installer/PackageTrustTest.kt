package io.dpcaio.installer

fun main() {
    val planner = PackageTrustPlanner()
    val expectation = PackageTrustExpectation(
        packageName = "com.example.app",
        versionCode = 7,
        signerSha256 = setOf("AA"),
        requiredSplits = setOf("config.en"),
    )

    val lineageOk = planner.evaluate(expectation, PackageTrustObservation(
        packageName = "com.example.app",
        versionCode = 7,
        currentSignerSha256 = setOf("BB"),
        signerLineageSha256 = setOf("AA", "BB"),
        hasMultipleSigners = false,
        installedSplits = setOf("config.en"),
        installSourceConfidence = InstallSourceConfidence.CONFIRMED,
        runtimeMerkleChecksums = mapOf("base" to "m1"),
        transportSha256 = mapOf("base" to "t1"),
    ))
    check(lineageOk.signerVerified)
    check(lineageOk.state == PackageTrustState.VERIFIED)

    val multiMismatch = planner.evaluate(expectation.copy(signerSha256 = setOf("AA", "CC")), PackageTrustObservation(
        packageName = "com.example.app",
        versionCode = 7,
        currentSignerSha256 = setOf("AA", "DD"),
        signerLineageSha256 = setOf("AA", "CC", "DD"),
        hasMultipleSigners = true,
        installedSplits = setOf("config.en"),
        installSourceConfidence = InstallSourceConfidence.PARTIAL,
    ))
    check(!multiMismatch.signerVerified)
    check(PackageTrustIssue.SIGNER_MISMATCH in multiMismatch.issues)

    val splitMismatch = planner.evaluate(expectation, lineageOk.observation.copy(installedSplits = setOf("config.fr")))
    check(PackageTrustIssue.MISSING_SPLIT in splitMismatch.issues)
    check(PackageTrustIssue.EXTRA_SPLIT in splitMismatch.issues)

    val transportOnly = planner.evaluate(expectation, lineageOk.observation.copy(runtimeMerkleChecksums = emptyMap()))
    check(transportOnly.runtimeIntegrityState == RuntimeIntegrityState.UNAVAILABLE)
    check(transportOnly.transportHashState == TransportHashState.PRESENT)

    println("PackageTrustTest: PASS")
}
