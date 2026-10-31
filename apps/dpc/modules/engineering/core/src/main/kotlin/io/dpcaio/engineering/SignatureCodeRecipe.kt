package io.dpcaio.engineering

enum class SignatureCodeInputKind {
    CERT_DER,
    CERT_SHA256_HEX,
    CERT_SHA1_HEX,
    PACKAGE_NAME_UTF8,
    PACKAGE_NAME_LOWER_UTF8,
    PACKAGE_NAME_UPPER_UTF8,
    CERT_DER_THEN_PACKAGE,
    PACKAGE_THEN_CERT_DER,
    CERT_SHA256_HEX_THEN_PACKAGE,
    PACKAGE_THEN_CERT_SHA256_HEX,
}

sealed interface SignatureCodeStep {
    data object Sha256 : SignatureCodeStep
    data object Sha1 : SignatureCodeStep
    data object Crc8 : SignatureCodeStep
    data object Crc16 : SignatureCodeStep
    data object Crc32 : SignatureCodeStep
    data object Xor8 : SignatureCodeStep
    data object Sum8 : SignatureCodeStep
    data object Sum16 : SignatureCodeStep
    data object HexUpper : SignatureCodeStep
    data object HexLower : SignatureCodeStep
    data object Ascii : SignatureCodeStep
    data object UIntBe : SignatureCodeStep
    data object UIntLe : SignatureCodeStep
    data class PrefixBytes(val count: Int) : SignatureCodeStep
    data class SuffixBytes(val count: Int) : SignatureCodeStep
    data class SliceBytes(val offset: Int, val length: Int) : SignatureCodeStep
    data class PrefixChars(val count: Int) : SignatureCodeStep
    data class SuffixChars(val count: Int) : SignatureCodeStep
    data class SliceChars(val offset: Int, val length: Int) : SignatureCodeStep
    data class Mod(val modulus: ULong) : SignatureCodeStep
    data object Decimal : SignatureCodeStep
    data class PadLeft(val width: Int, val pad: Char = '0') : SignatureCodeStep
    data object LuhnAppend : SignatureCodeStep
    data object LuhnValidate : SignatureCodeStep
}

data class SignatureCodeRecipe(
    val id: String,
    val displayName: String,
    val input: SignatureCodeInputKind,
    val steps: List<SignatureCodeStep>,
    val outputKind: DerivedCodeKind,
)

enum class SignatureCodeRecipeError {
    PIPELINE_TOO_LONG,
    INVALID_MODULUS,
    SLICE_OUT_OF_RANGE,
    TYPE_MISMATCH,
    INVALID_ARGUMENT,
}

sealed interface SignatureCodeRecipeValidation {
    data object Valid : SignatureCodeRecipeValidation
    data class Invalid(
        val error: SignatureCodeRecipeError,
        val message: String,
    ) : SignatureCodeRecipeValidation
}

private enum class SignatureValueType {
    BYTES,
    TEXT,
    UNSIGNED_NUMBER,
}

object SignatureCodeRecipeValidator {
    fun validate(
        recipe: SignatureCodeRecipe,
        limits: SignatureCodeLimits = SignatureCodeLimits(),
    ): SignatureCodeRecipeValidation {
        if (recipe.steps.size > limits.maxPipelineSteps) {
            return SignatureCodeRecipeValidation.Invalid(
                SignatureCodeRecipeError.PIPELINE_TOO_LONG,
                "Pipeline has ${recipe.steps.size} steps; max is ${limits.maxPipelineSteps}",
            )
        }

        var type = initialType(recipe.input)
        recipe.steps.forEachIndexed { index, step ->
            validateArguments(step)?.let { return it }
            val next = transition(type, step)
                ?: return SignatureCodeRecipeValidation.Invalid(
                    SignatureCodeRecipeError.TYPE_MISMATCH,
                    "Step $index (${step.javaClass.simpleName}) cannot consume $type",
                )
            type = next
        }
        return SignatureCodeRecipeValidation.Valid
    }

    private fun initialType(input: SignatureCodeInputKind): SignatureValueType {
        input.name
        return SignatureValueType.BYTES
    }

    private fun validateArguments(step: SignatureCodeStep): SignatureCodeRecipeValidation.Invalid? = when (step) {
        is SignatureCodeStep.Mod -> if (step.modulus == 0uL) {
            SignatureCodeRecipeValidation.Invalid(
                SignatureCodeRecipeError.INVALID_MODULUS,
                "MOD requires modulus > 0",
            )
        } else null

        is SignatureCodeStep.PrefixBytes -> invalidCount(step.count, "PrefixBytes")
        is SignatureCodeStep.SuffixBytes -> invalidCount(step.count, "SuffixBytes")
        is SignatureCodeStep.PrefixChars -> invalidCount(step.count, "PrefixChars")
        is SignatureCodeStep.SuffixChars -> invalidCount(step.count, "SuffixChars")
        is SignatureCodeStep.SliceBytes -> invalidSlice(step.offset, step.length, "SliceBytes")
        is SignatureCodeStep.SliceChars -> invalidSlice(step.offset, step.length, "SliceChars")
        is SignatureCodeStep.PadLeft -> if (step.width <= 0) {
            SignatureCodeRecipeValidation.Invalid(
                SignatureCodeRecipeError.INVALID_ARGUMENT,
                "PadLeft width must be > 0",
            )
        } else null

        else -> null
    }

    private fun invalidCount(count: Int, name: String): SignatureCodeRecipeValidation.Invalid? =
        if (count <= 0) {
            SignatureCodeRecipeValidation.Invalid(
                SignatureCodeRecipeError.SLICE_OUT_OF_RANGE,
                "$name count must be > 0",
            )
        } else null

    private fun invalidSlice(
        offset: Int,
        length: Int,
        name: String,
    ): SignatureCodeRecipeValidation.Invalid? =
        if (offset < 0 || length <= 0) {
            SignatureCodeRecipeValidation.Invalid(
                SignatureCodeRecipeError.SLICE_OUT_OF_RANGE,
                "$name requires offset >= 0 and length > 0",
            )
        } else null

    private fun transition(
        current: SignatureValueType,
        step: SignatureCodeStep,
    ): SignatureValueType? = when (step) {
        SignatureCodeStep.Sha256,
        SignatureCodeStep.Sha1,
        -> if (current == SignatureValueType.BYTES) SignatureValueType.BYTES else null

        SignatureCodeStep.Crc8,
        SignatureCodeStep.Crc16,
        SignatureCodeStep.Crc32,
        SignatureCodeStep.Xor8,
        SignatureCodeStep.Sum8,
        SignatureCodeStep.Sum16,
        -> if (current == SignatureValueType.BYTES) SignatureValueType.UNSIGNED_NUMBER else null

        SignatureCodeStep.HexUpper,
        SignatureCodeStep.HexLower,
        SignatureCodeStep.Ascii,
        -> if (current == SignatureValueType.BYTES) SignatureValueType.TEXT else null

        SignatureCodeStep.UIntBe,
        SignatureCodeStep.UIntLe,
        -> if (current == SignatureValueType.BYTES) SignatureValueType.UNSIGNED_NUMBER else null

        is SignatureCodeStep.PrefixBytes,
        is SignatureCodeStep.SuffixBytes,
        is SignatureCodeStep.SliceBytes,
        -> if (current == SignatureValueType.BYTES) SignatureValueType.BYTES else null

        is SignatureCodeStep.PrefixChars,
        is SignatureCodeStep.SuffixChars,
        is SignatureCodeStep.SliceChars,
        -> if (current == SignatureValueType.TEXT) SignatureValueType.TEXT else null

        is SignatureCodeStep.Mod -> if (current == SignatureValueType.UNSIGNED_NUMBER) SignatureValueType.UNSIGNED_NUMBER else null
        SignatureCodeStep.Decimal -> if (current == SignatureValueType.UNSIGNED_NUMBER) SignatureValueType.TEXT else null
        is SignatureCodeStep.PadLeft -> if (current == SignatureValueType.TEXT) SignatureValueType.TEXT else null
        SignatureCodeStep.LuhnAppend,
        SignatureCodeStep.LuhnValidate,
        -> if (current == SignatureValueType.TEXT) SignatureValueType.TEXT else null
    }
}
