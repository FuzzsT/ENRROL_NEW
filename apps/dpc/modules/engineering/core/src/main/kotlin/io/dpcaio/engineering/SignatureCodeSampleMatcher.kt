package io.dpcaio.engineering

data class SignatureCodeSample(
    val identity: AppSigningIdentity,
    val expectedCode: String,
    val outputKind: DerivedCodeKind? = null,
)

enum class SampleMatchConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNVERIFIED,
}

data class SignatureCodeSampleMatch(
    val recipe: SignatureCodeRecipe,
    val exactMatches: Int,
    val sampleCount: Int,
    val ambiguityCount: Int,
    val confidence: SampleMatchConfidence,
)

data class SampleMatcherLimits(
    val maxSamples: Int = 32,
    val maxRecipesEvaluated: Int = 5_000,
) {
    init {
        require(maxSamples in 1..32)
        require(maxRecipesEvaluated in 1..5_000)
    }
}

enum class SignatureCodeSampleMatcherError {
    NO_SAMPLES,
    SAMPLE_LIMIT_EXCEEDED,
    SEARCH_LIMIT_REACHED,
}

sealed interface SignatureCodeSampleMatcherResult {
    data class Success(
        val matches: List<SignatureCodeSampleMatch>,
        val evaluatedRecipes: Int,
    ) : SignatureCodeSampleMatcherResult

    data class SearchLimitReached(
        val matches: List<SignatureCodeSampleMatch>,
        val evaluatedRecipes: Int,
        val error: SignatureCodeSampleMatcherError = SignatureCodeSampleMatcherError.SEARCH_LIMIT_REACHED,
    ) : SignatureCodeSampleMatcherResult

    data class Failure(
        val error: SignatureCodeSampleMatcherError,
        val message: String,
    ) : SignatureCodeSampleMatcherResult
}

object SignatureCodeSampleMatcher {
    private val prefixSuffixWidths = setOf(2, 3, 4, 6, 8)
    private val decimalWidths = setOf(4, 5, 6, 7, 8)
    private val inputKinds = listOf(
        SignatureCodeInputKind.CERT_DER,
        SignatureCodeInputKind.CERT_SHA256_HEX,
        SignatureCodeInputKind.CERT_SHA1_HEX,
        SignatureCodeInputKind.PACKAGE_NAME_UTF8,
        SignatureCodeInputKind.PACKAGE_NAME_LOWER_UTF8,
        SignatureCodeInputKind.PACKAGE_NAME_UPPER_UTF8,
        SignatureCodeInputKind.CERT_DER_THEN_PACKAGE,
        SignatureCodeInputKind.PACKAGE_THEN_CERT_DER,
        SignatureCodeInputKind.CERT_SHA256_HEX_THEN_PACKAGE,
        SignatureCodeInputKind.PACKAGE_THEN_CERT_SHA256_HEX,
    )

    fun match(
        samples: List<SignatureCodeSample>,
        limits: SampleMatcherLimits = SampleMatcherLimits(),
    ): SignatureCodeSampleMatcherResult {
        if (samples.isEmpty()) {
            return SignatureCodeSampleMatcherResult.Failure(
                SignatureCodeSampleMatcherError.NO_SAMPLES,
                "At least one sample is required",
            )
        }
        if (samples.size > limits.maxSamples) {
            return SignatureCodeSampleMatcherResult.Failure(
                SignatureCodeSampleMatcherError.SAMPLE_LIMIT_EXCEEDED,
                "Sample count ${samples.size} exceeds max ${limits.maxSamples}",
            )
        }

        val recipes = candidateRecipes()
        val capped = recipes.take(limits.maxRecipesEvaluated)
        val scored = capped.mapNotNull { recipe -> scoreRecipe(recipe, samples) }
        val matches = finalizeMatches(scored, samples.size)
        return if (recipes.size > limits.maxRecipesEvaluated) {
            SignatureCodeSampleMatcherResult.SearchLimitReached(
                matches = matches,
                evaluatedRecipes = capped.size,
            )
        } else {
            SignatureCodeSampleMatcherResult.Success(
                matches = matches,
                evaluatedRecipes = capped.size,
            )
        }
    }

    private fun scoreRecipe(
        recipe: SignatureCodeRecipe,
        samples: List<SignatureCodeSample>,
    ): ScoredRecipe? {
        if (samples.any { it.outputKind != null && it.outputKind != recipe.outputKind }) return null

        val engine = SignatureCodeDerivationEngine()
        val outputs = ArrayList<String?>(samples.size)
        var exact = 0
        for (sample in samples) {
            val signerOutputs = sample.identity.currentSigners
                .mapNotNull { signer ->
                    val result = engine.derive(
                        identity = sample.identity,
                        recipe = recipe,
                        signer = signer,
                        source = SignatureCodeSource.SAMPLE_MATCH,
                    )
                    (result as? SignatureCodeDerivationResult.Success)?.candidate?.candidate
                }
                .distinct()
                .sorted()

            outputs += signerOutputs.takeIf { it.isNotEmpty() }?.joinToString("\u001F")
            if (sample.expectedCode in signerOutputs) exact++
        }
        if (exact == 0) return null
        return ScoredRecipe(recipe, exact, outputs)
    }

    private fun finalizeMatches(
        scored: List<ScoredRecipe>,
        sampleCount: Int,
    ): List<SignatureCodeSampleMatch> {
        val ambiguityByOutputs = scored.groupingBy { it.outputs }.eachCount()
        return scored.map { scoredRecipe ->
            val ambiguity = ambiguityByOutputs.getValue(scoredRecipe.outputs)
            SignatureCodeSampleMatch(
                recipe = scoredRecipe.recipe,
                exactMatches = scoredRecipe.exactMatches,
                sampleCount = sampleCount,
                ambiguityCount = ambiguity,
                confidence = confidence(
                    exactMatches = scoredRecipe.exactMatches,
                    sampleCount = sampleCount,
                    ambiguityCount = ambiguity,
                ),
            )
        }.sortedWith(
            compareByDescending<SignatureCodeSampleMatch> { it.exactMatches }
                .thenBy { it.ambiguityCount }
                .thenBy { it.recipe.steps.size }
                .thenBy { it.recipe.id }
        )
    }

    private fun confidence(
        exactMatches: Int,
        sampleCount: Int,
        ambiguityCount: Int,
    ): SampleMatchConfidence {
        if (sampleCount < 2) return SampleMatchConfidence.UNVERIFIED
        if (exactMatches == sampleCount) {
            return when {
                sampleCount >= 3 && ambiguityCount == 1 -> SampleMatchConfidence.HIGH
                ambiguityCount <= 3 -> SampleMatchConfidence.MEDIUM
                else -> SampleMatchConfidence.LOW
            }
        }
        val ratio = exactMatches.toDouble() / sampleCount.toDouble()
        return if (sampleCount >= 4 && ratio >= 0.80 && ambiguityCount == 1) {
            SampleMatchConfidence.MEDIUM
        } else {
            SampleMatchConfidence.LOW
        }
    }

    private fun candidateRecipes(): List<SignatureCodeRecipe> {
        val recipes = LinkedHashMap<String, SignatureCodeRecipe>()
        fun add(recipe: SignatureCodeRecipe) {
            val key = canonicalRecipeKey(recipe)
            recipes.putIfAbsent(key, recipe)
        }

        AutoSignatureCodeCatalog.recipes().forEach(::add)

        for (input in inputKinds) {
            for (hash in listOf(HashKind.SHA256, HashKind.SHA1)) {
                for (side in WindowSide.entries) {
                    for (width in prefixSuffixWidths) {
                        for (endian in Endian.entries) {
                            for (digits in decimalWidths) {
                                add(hashWindowRecipe(input, hash, side, width, endian, digits))
                            }
                        }
                    }
                }
            }

            for (checksum in ChecksumKind.entries) {
                for (digits in decimalWidths) {
                    add(checksumRecipe(input, checksum, digits))
                }
            }

            for (side in WindowSide.entries) {
                for (width in prefixSuffixWidths) {
                    for (endian in Endian.entries) {
                        for (digits in decimalWidths) {
                            add(rawWindowRecipe(input, side, width, endian, digits))
                        }
                    }
                }
            }
        }
        return recipes.values.toList()
    }

    private fun hashWindowRecipe(
        input: SignatureCodeInputKind,
        hash: HashKind,
        side: WindowSide,
        width: Int,
        endian: Endian,
        digits: Int,
    ): SignatureCodeRecipe {
        val steps = listOf(
            hash.step,
            side.step(width),
            endian.step,
            SignatureCodeStep.Mod(decimalModulus(digits)),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(digits),
        )
        return SignatureCodeRecipe(
            id = "sample-${hash.id}-${input.name.lowercase()}-${side.id}$width-${endian.id}-mod$digits",
            displayName = "${hash.label} ${input.name} ${side.label} $width ${endian.label} → $digits digits",
            input = input,
            steps = steps,
            outputKind = DerivedCodeKind.NUMERIC,
        )
    }

    private fun checksumRecipe(
        input: SignatureCodeInputKind,
        checksum: ChecksumKind,
        digits: Int,
    ): SignatureCodeRecipe = SignatureCodeRecipe(
        id = "sample-${checksum.id}-${input.name.lowercase()}-mod$digits",
        displayName = "${checksum.label} ${input.name} → $digits digits",
        input = input,
        steps = listOf(
            checksum.step,
            SignatureCodeStep.Mod(decimalModulus(digits)),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(digits),
        ),
        outputKind = DerivedCodeKind.NUMERIC,
    )

    private fun rawWindowRecipe(
        input: SignatureCodeInputKind,
        side: WindowSide,
        width: Int,
        endian: Endian,
        digits: Int,
    ): SignatureCodeRecipe = SignatureCodeRecipe(
        id = "sample-raw-${input.name.lowercase()}-${side.id}$width-${endian.id}-mod$digits",
        displayName = "Raw ${input.name} ${side.label} $width ${endian.label} → $digits digits",
        input = input,
        steps = listOf(
            side.step(width),
            endian.step,
            SignatureCodeStep.Mod(decimalModulus(digits)),
            SignatureCodeStep.Decimal,
            SignatureCodeStep.PadLeft(digits),
        ),
        outputKind = DerivedCodeKind.NUMERIC,
    )

    private fun canonicalRecipeKey(recipe: SignatureCodeRecipe): String = buildString {
        append(recipe.input.name)
        append('|')
        append(recipe.outputKind.name)
        recipe.steps.forEach { step ->
            append('|')
            append(step.toString())
        }
    }

    private fun decimalModulus(digits: Int): ULong {
        var result = 1uL
        repeat(digits) { result *= 10uL }
        return result
    }

    private enum class HashKind(
        val id: String,
        val label: String,
        val step: SignatureCodeStep,
    ) {
        SHA256("sha256", "SHA-256", SignatureCodeStep.Sha256),
        SHA1("sha1", "SHA-1", SignatureCodeStep.Sha1),
    }

    private enum class ChecksumKind(
        val id: String,
        val label: String,
        val step: SignatureCodeStep,
    ) {
        CRC8("crc8", "CRC-8/SMBUS", SignatureCodeStep.Crc8),
        CRC16("crc16", "CRC-16/CCITT-FALSE", SignatureCodeStep.Crc16),
        CRC32("crc32", "CRC-32", SignatureCodeStep.Crc32),
        XOR8("xor8", "XOR8", SignatureCodeStep.Xor8),
        SUM8("sum8", "SUM8", SignatureCodeStep.Sum8),
        SUM16("sum16", "SUM16", SignatureCodeStep.Sum16),
    }

    private enum class WindowSide(val id: String, val label: String) {
        PREFIX("prefix", "prefix"),
        SUFFIX("suffix", "suffix");

        fun step(width: Int): SignatureCodeStep = when (this) {
            PREFIX -> SignatureCodeStep.PrefixBytes(width)
            SUFFIX -> SignatureCodeStep.SuffixBytes(width)
        }
    }

    private enum class Endian(val id: String, val label: String, val step: SignatureCodeStep) {
        BE("be", "BE", SignatureCodeStep.UIntBe),
        LE("le", "LE", SignatureCodeStep.UIntLe),
    }

    private data class ScoredRecipe(
        val recipe: SignatureCodeRecipe,
        val exactMatches: Int,
        val outputs: List<String?>,
    )
}
