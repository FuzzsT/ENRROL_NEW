package io.dpcaio.engineering

enum class SecretCodeInventoryStatus {
    VERIFIED,
    PERMISSION_GATED,
    DISABLED,
    PATTERN_ONLY,
}

data class SecretCodeLiveReceiver(
    val packageName: String,
    val className: String,
    val action: String,
    val enabled: Boolean,
    val exported: Boolean,
    val requiredPermission: String?,
)

data class SecretCodeInventoryRecord(
    val packageName: String,
    val code: String?,
    val rawHost: String?,
    val receiverClassName: String,
    val actions: Set<String>,
    val scheme: String?,
    val status: SecretCodeInventoryStatus,
    val enabled: Boolean?,
    val exported: Boolean?,
    val requiredPermission: String?,
    val sourceKinds: Set<SecretCodeSourceKind>,
    val sourceApkNames: Set<String>,
)
