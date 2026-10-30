package io.dpcaio.app.engineering

enum class SecretCodeRisk {
    SAFE_INFO,
    DIAGNOSTIC,
    SETTINGS,
    UNKNOWN_RISK,
}

data class SecretCodeEntry(
    val code: String,
    val label: String,
    val risk: SecretCodeRisk,
    val executable: Boolean,
)

object SamsungSecretCodeCatalog {
    /**
     * User-supplied codes default to UNKNOWN_RISK. The app may inspect receiver
     * capability for them, but it does not auto-execute unknown codes.
     */
    fun userSupplied(code: String): SecretCodeEntry = SecretCodeEntry(
        code = normalize(code),
        label = "User supplied secret code",
        risk = SecretCodeRisk.UNKNOWN_RISK,
        executable = false,
    )

    fun normalize(code: String): String {
        val trimmed = code.trim()
        return if (trimmed.startsWith("*#*#") && trimmed.endsWith("#*#*") && trimmed.length > 8) {
            trimmed.substring(4, trimmed.length - 4)
        } else {
            trimmed
        }
    }
}
