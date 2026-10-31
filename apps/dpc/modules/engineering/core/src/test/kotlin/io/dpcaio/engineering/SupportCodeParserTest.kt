package io.dpcaio.engineering

fun main() {
    val wrapper = SupportCodeParser.parse("  *#*#4636#*#*  ")
    check(wrapper.original == "  *#*#4636#*#*  ")
    val android = wrapper.interpretations.single { it.format == SupportCodeFormat.ANDROID_SECRET_CODE_WRAPPER }
    check(android.normalized == "*#*#4636#*#*")
    check(android.fields["inner"] == "4636")

    val generic = SupportCodeParser.parse("*123#")
    check(generic.interpretations.any { it.format == SupportCodeFormat.MMI_USSD })
    check(generic.interpretations.none { it.format == SupportCodeFormat.ANDROID_SECRET_CODE_WRAPPER })

    val uri = SupportCodeParser.parse("android_secret_code://4636")
    val uriInterpretation = uri.interpretations.single { it.format == SupportCodeFormat.SECRET_CODE_URI }
    check(uriInterpretation.fields["code"] == "4636")

    val ambiguous = SupportCodeParser.parse("1234")
    check(ambiguous.interpretations.size >= 2)
    check(ambiguous.interpretations.any { it.format == SupportCodeFormat.NUMERIC })
    check(ambiguous.interpretations.any { it.format == SupportCodeFormat.HEX })

    val limited = SupportCodeParser.parse("x".repeat(33), SupportCodeLimits(maxInputChars = 32))
    check(limited.interpretations.isEmpty())
    check(limited.errors.any { it.contains("maxInputChars") })

    println("SupportCodeParserTest: PASS")
}
