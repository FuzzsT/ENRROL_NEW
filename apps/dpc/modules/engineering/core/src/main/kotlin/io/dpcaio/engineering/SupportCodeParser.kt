package io.dpcaio.engineering

enum class SupportCodeFormat {
    ANDROID_SECRET_CODE_WRAPPER,
    SECRET_CODE_URI,
    MMI_USSD,
    NUMERIC,
    HEX,
}

data class SupportCodeLimits(
    val maxInputChars: Int = 256,
)

data class SupportCodeInterpretation(
    val format: SupportCodeFormat,
    val normalized: String,
    val fields: Map<String, String> = emptyMap(),
)

data class SupportCodeParseResult(
    val original: String,
    val interpretations: List<SupportCodeInterpretation>,
    val errors: List<String> = emptyList(),
)

/**
 * Pure read-only parser for support/service-code text.
 *
 * It only classifies text. It never creates intents, dials, broadcasts, invokes
 * receivers, executes shell commands, or otherwise attempts to run a code.
 */
object SupportCodeParser {
    private val androidWrapper = Regex("^\\*#\\*#([^#*]+)#\\*#\\*$")
    private val numeric = Regex("^[0-9]+$")
    private val hexadecimal = Regex("^[0-9A-Fa-f]+$")

    fun parse(
        input: String,
        limits: SupportCodeLimits = SupportCodeLimits(),
    ): SupportCodeParseResult {
        require(limits.maxInputChars >= 0) { "maxInputChars must be non-negative" }
        if (input.length > limits.maxInputChars) {
            return SupportCodeParseResult(
                original = input,
                interpretations = emptyList(),
                errors = listOf("input exceeds maxInputChars=${limits.maxInputChars}"),
            )
        }

        val value = input.trim()
        if (value.isEmpty()) {
            return SupportCodeParseResult(input, emptyList(), listOf("input is empty"))
        }

        val interpretations = mutableListOf<SupportCodeInterpretation>()

        androidWrapper.matchEntire(value)?.let { match ->
            interpretations += SupportCodeInterpretation(
                format = SupportCodeFormat.ANDROID_SECRET_CODE_WRAPPER,
                normalized = value,
                fields = mapOf("inner" to match.groupValues[1]),
            )
        }

        parseSecretCodeUri(value)?.let { code ->
            interpretations += SupportCodeInterpretation(
                format = SupportCodeFormat.SECRET_CODE_URI,
                normalized = "android_secret_code://$code",
                fields = mapOf("code" to code),
            )
        }

        if ((value.startsWith('*') || value.startsWith('#')) && value.endsWith('#')) {
            interpretations += SupportCodeInterpretation(
                format = SupportCodeFormat.MMI_USSD,
                normalized = value,
            )
        }

        if (numeric.matches(value)) {
            interpretations += SupportCodeInterpretation(
                format = SupportCodeFormat.NUMERIC,
                normalized = value,
            )
        }

        if (hexadecimal.matches(value)) {
            interpretations += SupportCodeInterpretation(
                format = SupportCodeFormat.HEX,
                normalized = value.uppercase(),
            )
        }

        return SupportCodeParseResult(
            original = input,
            interpretations = interpretations.distinctBy { it.format },
        )
    }

    private fun parseSecretCodeUri(value: String): String? {
        val prefix = "android_secret_code://"
        if (!value.startsWith(prefix, ignoreCase = true)) return null
        return value.substring(prefix.length).takeIf { code ->
            code.isNotBlank() && '/' !in code && '?' !in code && '#' !in code
        }
    }
}
