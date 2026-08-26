package io.dpcaio.offline

enum class OfflineMode { ONLINE, ONLINE_PREFERRED, FULL_OFFLINE, OFFLINE_THEN_SYNC }

enum class OfflineStage {
    BUNDLE_RECEIVED,
    BUNDLE_VERIFIED,
    PACKAGES_STAGED,
    PACKAGES_INSTALLED,
    PERMISSIONS_APPLIED,
    COMPONENTS_APPLIED,
    POLICIES_APPLIED,
    READBACK_VERIFIED,
    OFFLINE_VERIFIED,
    SYNC_PENDING,
    SYNCED,
    FAILED
}

enum class OfflineCapability {
    PACKAGE_INSTALL,
    PERMISSION_CONTROL,
    COMPONENT_CONTROL,
    DEVICE_POLICY
}

data class OfflinePackageFile(
    val path: String,
    val sha256: String,
    val required: Boolean = true
)

data class OfflinePackageEntry(
    val packageName: String,
    val versionCode: Long,
    val signingCertificateSha256: String,
    val files: List<OfflinePackageFile>
) {
    constructor(
        packageName: String,
        versionCode: Long,
        baseFile: String,
        requiredSplits: List<String>,
        sha256: String,
        signingCertificateSha256: String,
        fileSha256: Map<String, String> = emptyMap()
    ) : this(
        packageName = packageName,
        versionCode = versionCode,
        signingCertificateSha256 = signingCertificateSha256,
        files = buildList {
            add(OfflinePackageFile(baseFile, fileSha256[baseFile] ?: sha256, required = true))
            requiredSplits.forEach { split ->
                add(OfflinePackageFile(split, fileSha256[split].orEmpty(), required = true))
            }
        }
    )

    val baseFile: String
        get() = files.firstOrNull { it.path.endsWith("/base.apk") || it.path == "base.apk" }?.path
            ?: files.firstOrNull()?.path.orEmpty()

    val requiredSplits: List<String>
        get() = files.filter { it.required && it.path != baseFile }.map { it.path }

    val sha256: String
        get() = files.firstOrNull { it.path == baseFile }?.sha256.orEmpty()

    val fileSha256: Map<String, String>
        get() = files.associate { it.path to it.sha256 }
}

data class OfflineBundleManifest(
    val schemaVersion: Int,
    val bundleId: String,
    val minimumDpcVersion: String,
    val minimumAndroidApi: Int,
    val allowedModes: Set<String>,
    val packages: List<OfflinePackageEntry>,
    val requiredCapabilities: Set<OfflineCapability>,
    val organizationId: String = "",
    val policyPath: String? = null
)

enum class OfflineReadinessStatus { FULL_OFFLINE_READY, OFFLINE_PROFILE_INCOMPATIBLE, OFFLINE_BUNDLE_INVALID }

data class OfflineReadinessContext(
    val signatureVerified: Boolean,
    val manifestHashVerified: Boolean,
    val schemaSupported: Boolean,
    val currentDpcVersion: String,
    val androidApi: Int,
    val provisioningMode: String,
    val availablePackages: Set<String>,
    val availableCapabilities: Set<OfflineCapability>
)

data class OfflineReadiness(
    val status: OfflineReadinessStatus,
    val blockingCode: String? = null,
    val details: List<String> = blockingCode?.let(::listOf) ?: emptyList()
)

data class OfflineReadinessInput(
    val signatureVerified: Boolean,
    val schemaSupported: Boolean,
    val currentAndroidApi: Int,
    val provisioningMode: String,
    val currentDpcVersion: String,
    val availablePackageFiles: Set<String>,
    val availableCapabilities: Set<String>
)
