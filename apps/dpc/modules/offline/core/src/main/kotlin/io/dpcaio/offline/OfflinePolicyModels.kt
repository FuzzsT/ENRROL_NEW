package io.dpcaio.offline

enum class OfflineDefaultPermissionPolicy { PROMPT, AUTO_GRANT, AUTO_DENY }
enum class OfflinePermissionDesiredState { DEFAULT, GRANTED, DENIED }
enum class OfflineComponentDesiredState { DEFAULT, ENABLED, DISABLED }

data class OfflinePermissionRule(
    val packageName: String,
    val permission: String,
    val state: OfflinePermissionDesiredState,
    val targetUserId: Int? = null,
    val required: Boolean = true
)

data class OfflineComponentRule(
    val packageName: String,
    val className: String,
    val state: OfflineComponentDesiredState,
    val targetUserId: Int? = null,
    val required: Boolean = true
) {
    val normalizedClassName: String get() = if (className.startsWith('.')) packageName + className else className
}

data class OfflinePolicySpec(
    val defaultPermissionPolicy: OfflineDefaultPermissionPolicy? = null,
    val permissions: List<OfflinePermissionRule> = emptyList(),
    val components: List<OfflineComponentRule> = emptyList()
)
