package io.dpcaio.selfupdate

enum class UpdateChannel {
    STABLE,
    CONTINUOUS,
}

data class SelfUpdateManifest(
    val schemaVersion: Int,
    val channel: UpdateChannel,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long?,
    val publishedAt: String,
    val releaseNotesUrl: String?,
)

enum class SelfUpdateState {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    VERIFIED,
    INSTALLING,
    WAITING_USER,
    UPDATED,
    ERROR,
}

enum class SelfUpdateInstallPolicy {
    ASK_ALWAYS,
    SILENT_IF_ALLOWED,
    AUTO_SILENT,
}

data class SelfUpdateDecision(
    val accepted: Boolean,
    val state: SelfUpdateState,
    val reason: String,
)
