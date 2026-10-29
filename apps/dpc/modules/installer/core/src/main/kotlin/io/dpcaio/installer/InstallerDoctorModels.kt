package io.dpcaio.installer

enum class InstallerBackendKind {
    PACKAGE_INSTALLER,
    DEVICE_OWNER_CONTEXT,
    SHIZUKU_DIAGNOSTIC,
}

enum class InstallerBackendAvailability {
    READY,
    NEEDS_USER_ACTION,
    DIAGNOSTIC_ONLY,
    UNAVAILABLE,
}

data class InstallerBackendState(
    val kind: InstallerBackendKind,
    val availability: InstallerBackendAvailability,
    val detail: String,
)

data class InstallerDoctorSnapshot(
    val packageInstallerAvailable: Boolean,
    val canRequestPackageInstalls: Boolean,
    val deviceOwner: Boolean,
    val profileOwner: Boolean,
    val shizukuBinderAlive: Boolean,
    val shizukuPermissionGranted: Boolean,
    val backends: List<InstallerBackendState>,
)

enum class InstallHistoryStatus {
    PENDING_USER_ACTION,
    SUCCEEDED,
    FAILED,
}

data class InstallHistoryRecord(
    val timestampMillis: Long,
    val status: InstallHistoryStatus,
    val packageName: String,
    val detail: String?,
)
