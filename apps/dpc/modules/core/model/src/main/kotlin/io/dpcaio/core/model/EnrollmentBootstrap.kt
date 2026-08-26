package io.dpcaio.core.model

data class BootstrapBaseline(
    val autoTime: Boolean? = null,
    val networkLogging: Boolean? = null,
    val securityLogging: Boolean? = null,
)

data class BootstrapPolicy(
    val schemaVersion: Int,
    val profileId: String,
    val allowedModes: Set<String>,
    val minimumAndroidApi: Int,
    val minimumDpcVersion: String,
    val requiredCapabilities: Set<String> = emptySet(),
    val baseline: BootstrapBaseline = BootstrapBaseline(),
) {
    fun validate(requestedMode: String, androidApi: Int, dpcVersion: String): BootstrapValidationResult {
        if (schemaVersion != 1) return BootstrapValidationResult(false, "UNSUPPORTED_SCHEMA")
        if (profileId.isBlank()) return BootstrapValidationResult(false, "PROFILE_INVALID")
        if (requestedMode !in allowedModes) return BootstrapValidationResult(false, "MODE_NOT_ALLOWED")
        if (androidApi < minimumAndroidApi) return BootstrapValidationResult(false, "ANDROID_API_TOO_OLD")
        if (compareVersions(dpcVersion, minimumDpcVersion) < 0) return BootstrapValidationResult(false, "DPC_VERSION_TOO_OLD")
        return BootstrapValidationResult(true, null)
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val cmp = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}

data class BootstrapValidationResult(val ok: Boolean, val errorCode: String?)
