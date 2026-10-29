package io.dpcaio.app

enum class InstallQueueState {
    QUEUED,
    PREFLIGHT,
    WAITING_USER,
    INSTALLING,
    SUCCESS,
    FAILED,
    SKIPPED,
}

data class InstallQueueItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val updaterMode: Boolean,
    val targetPackage: String?,
    val state: InstallQueueState = InstallQueueState.QUEUED,
    val sessionId: Int? = null,
    val resolvedPackage: String? = null,
    val versionCode: Long? = null,
    val detail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class InstallHistoryEntry(
    val itemId: String,
    val timestamp: Long,
    val packageName: String?,
    val versionCode: Long?,
    val displayName: String,
    val result: InstallQueueState,
    val detail: String?,
)
