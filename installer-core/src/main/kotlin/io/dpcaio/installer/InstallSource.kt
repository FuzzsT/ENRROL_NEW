package io.dpcaio.installer

enum class PackageSource {
    STORE,
    LOCAL_FILE,
    DOWNLOADED_FILE,
    OTHER,
    UNSPECIFIED
}

data class InstallSourceSnapshot(
    val initiatingPackageName: String?,
    val installingPackageName: String?,
    val packageSource: PackageSource,
    val updateOwnerPackageName: String?
)

enum class InstallSourceClass {
    REAL_PLAY,
    INSTALLER_RECORD_PLAY,
    STORE_METADATA_ONLY,
    OTHER
}
