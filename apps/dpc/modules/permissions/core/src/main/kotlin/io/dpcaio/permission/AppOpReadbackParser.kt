package io.dpcaio.permission

data class AppOpReadback(
    val canonicalName: String?,
    val uidMode: String?,
    val packageMode: String?,
    val defaultMode: String?,
) {
    val effectiveMode: String?
        get() = uidMode ?: packageMode ?: defaultMode
}

object AppOpReadbackParser {
    private val allowedModes = setOf("allow", "ignore", "deny", "default", "foreground")
    private val unreadable = setOf("INVALID_ARGUMENT", "TIMEOUT", "EXECUTION_ERROR")

    fun parse(snapshot: String?): AppOpReadback? {
        val raw = snapshot?.trim().orEmpty()
        if (raw.isBlank() || raw in unreadable) return null

        var canonicalName: String? = null
        var uidMode: String? = null
        var packageMode: String? = null
        var defaultMode: String? = null

        raw.lineSequence().map(String::trim).forEach { line ->
            when {
                line.startsWith("Uid mode:", ignoreCase = true) -> {
                    parseNamedMode(line.substringAfter("Uid mode:").trim())?.let { (name, mode) ->
                        canonicalName = name
                        uidMode = mode
                    }
                }
                line.startsWith("Default mode:", ignoreCase = true) -> {
                    defaultMode = line.substringAfter("Default mode:").trim().lowercase()
                        .takeIf { it in allowedModes }
                }
                else -> {
                    parseNamedMode(line)?.let { (name, mode) ->
                        canonicalName = canonicalName ?: name
                        packageMode = mode
                    }
                }
            }
        }

        if (uidMode == null && packageMode == null && defaultMode == null) return null
        return AppOpReadback(canonicalName, uidMode, packageMode, defaultMode)
    }

    private fun parseNamedMode(line: String): Pair<String, String>? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val name = line.substring(0, colon).trim()
        val tail = line.substring(colon + 1).trim()
        val mode = tail.substringBefore(' ').substringBefore(';').trim().lowercase()
        if (name.isBlank() || mode !in allowedModes) return null
        return name to mode
    }
}
