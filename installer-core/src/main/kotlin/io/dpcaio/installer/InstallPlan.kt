package io.dpcaio.installer

enum class InstallPreference {
    DEFAULT,
    PLAY_COMPAT
}

enum class InstallRoute {
    MANAGED_PLAY,
    DPC_PACKAGE_INSTALLER,
    SYSTEM_PRIVILEGED,
    SHIZUKU,
    USER_CONFIRMATION,
    INSTALLER_RECORD_COMPAT
}

data class InstallRequest(
    val packageName: String,
    val preference: InstallPreference,
    val requireRealPlay: Boolean = false,
    val allowInstallerRecordFallback: Boolean = false
)

data class InstallAvailability(
    val managedPlay: Boolean = false,
    val managedPlayVerified: Boolean = false,
    val dpcPackageInstaller: Boolean = false,
    val systemPrivileged: Boolean = false,
    val shizuku: Boolean = false,
    val userConfirmation: Boolean = false,
    val installerRecordCompat: Boolean = false
)

data class InstallPlan(
    val request: InstallRequest,
    val routes: List<InstallRoute>,
    val blockers: List<String>,
    val realPlayVerified: Boolean
) {
    val selected: InstallRoute? get() = routes.firstOrNull()
}
