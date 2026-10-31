package io.dpcaio.permission

enum class RuntimePermissionReadbackState {
    GRANTED,
    DENIED,
    UNKNOWN,
}

object RuntimePermissionReadbackParser {
    private val userHeader = Regex("User\\s+(\\d+):")
    private val grantedPattern = Regex("\\bgranted=(true|false)\\b", RegexOption.IGNORE_CASE)

    fun parse(dump: String, userId: Int, permissionName: String): RuntimePermissionReadbackState {
        if (userId < 0 || permissionName.isBlank()) return RuntimePermissionReadbackState.UNKNOWN
        if (dump.isBlank() || dump == "TIMEOUT" || dump == "EXECUTION_ERROR" || dump == "INVALID_ARGUMENT") {
            return RuntimePermissionReadbackState.UNKNOWN
        }

        val lines = dump.lineSequence().toList()
        val start = lines.indexOfFirst { line ->
            userHeader.matchEntire(line.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() == userId
        }
        if (start < 0) return RuntimePermissionReadbackState.UNKNOWN

        for (index in (start + 1) until lines.size) {
            val trimmed = lines[index].trim()
            if (userHeader.matches(trimmed)) break
            if (!trimmed.startsWith("$permissionName:")) continue
            return when (grantedPattern.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase()) {
                "true" -> RuntimePermissionReadbackState.GRANTED
                "false" -> RuntimePermissionReadbackState.DENIED
                else -> RuntimePermissionReadbackState.UNKNOWN
            }
        }

        return RuntimePermissionReadbackState.DENIED
    }
}
