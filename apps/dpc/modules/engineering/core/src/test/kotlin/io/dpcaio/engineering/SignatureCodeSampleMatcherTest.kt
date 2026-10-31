package io.dpcaio.engineering

import java.security.MessageDigest

private fun sampleSigner(seed: Int, emptyDer: Boolean = false): SigningCertificateIdentity {
    val der = if (emptyDer) ByteArray(0) else ByteArray(24) { index -> ((seed * 37 + index * 11) and 0xFF).toByte() }
    fun digest(algorithm: String): String = MessageDigest.getInstance(algorithm)
        .digest(der)
        .joinToString("") { "%02X".format(it) }
    return SigningCertificateIdentity(
        derBytes = der,
        sha256Hex = digest("SHA-256"),
        sha1Hex = digest("SHA-1"),
    )
}

private fun sampleIdentity(seed: Int, emptyDer: Boolean = false): AppSigningIdentity = AppSigningIdentity(
    packageName = "com.example.sample$seed",
    versionCode = seed.toLong(),
    currentSigners = listOf(sampleSigner(seed, emptyDer)),
)

private fun deriveExpected(identity: AppSigningIdentity, recipe: SignatureCodeRecipe): String {
    val result = SignatureCodeDerivationEngine().derive(identity, recipe, identity.currentSigners.single())
    check(result is SignatureCodeDerivationResult.Success)
    return result.candidate.candidate
}

fun main() {
    val target = AutoSignatureCodeCatalog.recipes().first { it.id == "sha256-cert-first4-six" }
    val threeSamples = (1..3).map { seed ->
        val identity = sampleIdentity(seed)
        SignatureCodeSample(
            identity = identity,
            expectedCode = deriveExpected(identity, target),
            outputKind = DerivedCodeKind.NUMERIC,
        )
    }

    val highResult = SignatureCodeSampleMatcher.match(threeSamples)
    check(highResult is SignatureCodeSampleMatcherResult.Success)
    val exactHigh = highResult.matches.filter { it.exactMatches == 3 }
    check(exactHigh.isNotEmpty())
    check(exactHigh.any { it.confidence == SampleMatchConfidence.HIGH }) {
        "three exact samples with a low-ambiguity rule should allow HIGH confidence"
    }
    check(highResult.evaluatedRecipes in 1..5_000)

    val twoResult = SignatureCodeSampleMatcher.match(threeSamples.take(2))
    check(twoResult is SignatureCodeSampleMatcherResult.Success)
    check(twoResult.matches.filter { it.exactMatches == 2 }.all { it.confidence != SampleMatchConfidence.HIGH }) {
        "two samples must never produce HIGH confidence"
    }

    val oneResult = SignatureCodeSampleMatcher.match(threeSamples.take(1))
    check(oneResult is SignatureCodeSampleMatcherResult.Success)
    check(oneResult.matches.filter { it.exactMatches == 1 }.all { it.confidence == SampleMatchConfidence.UNVERIFIED }) {
        "single-sample matches must remain UNVERIFIED"
    }

    val ambiguousSamples = (10..12).map { seed ->
        SignatureCodeSample(
            identity = sampleIdentity(seed, emptyDer = true),
            expectedCode = "0000",
            outputKind = DerivedCodeKind.NUMERIC,
        )
    }
    val ambiguousResult = SignatureCodeSampleMatcher.match(ambiguousSamples)
    check(ambiguousResult is SignatureCodeSampleMatcherResult.Success)
    val ambiguousExact = ambiguousResult.matches.filter { it.exactMatches == ambiguousSamples.size }
    check(ambiguousExact.size > 1) { "empty-byte fixtures should produce multiple equivalent zero-valued rules" }
    check(ambiguousExact.all { it.ambiguityCount > 1 })
    check(ambiguousExact.all { it.confidence != SampleMatchConfidence.HIGH }) {
        "ambiguous exact matches must not be HIGH confidence"
    }

    val firstSigner = sampleSigner(30)
    val secondSigner = sampleSigner(31)
    val multiSignerIdentity = AppSigningIdentity(
        packageName = "com.example.multisigner",
        versionCode = 99L,
        currentSigners = listOf(firstSigner, secondSigner),
    )
    val secondSignerExpected = SignatureCodeDerivationEngine().derive(
        identity = multiSignerIdentity,
        recipe = target,
        signer = secondSigner,
    )
    check(secondSignerExpected is SignatureCodeDerivationResult.Success)
    val multiSignerResult = SignatureCodeSampleMatcher.match(
        listOf(
            SignatureCodeSample(
                identity = multiSignerIdentity,
                expectedCode = secondSignerExpected.candidate.candidate,
                outputKind = DerivedCodeKind.NUMERIC,
            )
        )
    )
    check(multiSignerResult is SignatureCodeSampleMatcherResult.Success)
    check(multiSignerResult.matches.any { it.recipe.id == target.id && it.exactMatches == 1 }) {
        "matcher must evaluate every current signer; the expected sample is derived from the second signer"
    }

    val tooManySamples = List(33) { index -> threeSamples[index % threeSamples.size] }
    val sampleLimit = SignatureCodeSampleMatcher.match(tooManySamples)
    check(sampleLimit is SignatureCodeSampleMatcherResult.Failure)
    check(sampleLimit.error == SignatureCodeSampleMatcherError.SAMPLE_LIMIT_EXCEEDED)

    val searchLimit = SignatureCodeSampleMatcher.match(
        samples = threeSamples,
        limits = SampleMatcherLimits(maxRecipesEvaluated = 1),
    )
    check(searchLimit is SignatureCodeSampleMatcherResult.SearchLimitReached)
    check(searchLimit.evaluatedRecipes == 1)
    check(searchLimit.matches.size <= 1)

    println("SignatureCodeSampleMatcherTest: PASS")
}
