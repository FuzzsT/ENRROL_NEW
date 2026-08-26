package io.dpcaio.permission

enum class PermissionProtection {
    NORMAL,
    DANGEROUS,
    SIGNATURE,
    PRIVILEGED,
    SIGNATURE_PRIVILEGED,
    APPOP,
    SPECIAL_ACCESS,
    INTERNAL,
    UNKNOWN
}

enum class PermissionOrigin {
    ANDROID_PLATFORM,
    SAMSUNG_VENDOR,
    GOOGLE_GMS,
    OTHER_VENDOR,
    UNKNOWN
}

data class PermissionCatalogEntry(
    val name: String,
    val group: String?,
    val declaringPackage: String?,
    val protection: PermissionProtection,
    val publicSdkConstant: Boolean,
    val description: String? = null,
    val rawProtectionLevel: Int = 0,
    val permissionFlags: Int = 0
)

object PermissionCatalogClassifier {
    fun classifyOrigin(entry: PermissionCatalogEntry): PermissionOrigin = when {
        entry.name.startsWith("android.") || entry.declaringPackage == "android" -> PermissionOrigin.ANDROID_PLATFORM
        entry.name.startsWith("com.sec.") || entry.name.startsWith("com.samsung.") || entry.declaringPackage?.startsWith("com.sec.") == true || entry.declaringPackage?.startsWith("com.samsung.") == true -> PermissionOrigin.SAMSUNG_VENDOR
        entry.name.startsWith("com.google.android.gms.") || entry.declaringPackage == "com.google.android.gms" -> PermissionOrigin.GOOGLE_GMS
        entry.name.contains('.') -> PermissionOrigin.OTHER_VENDOR
        else -> PermissionOrigin.UNKNOWN
    }

    fun isUndocumentedCandidate(entry: PermissionCatalogEntry): Boolean = !entry.publicSdkConstant ||
        classifyOrigin(entry) != PermissionOrigin.ANDROID_PLATFORM
}

data class PermissionGroupCatalogEntry(
    val name: String,
    val declaringPackage: String?,
    val publicSdkConstant: Boolean,
    val description: String? = null,
    val rawProtectionLevel: Int = 0,
    val permissionFlags: Int = 0
)

data class PermissionCatalogSnapshot(
    val groups: List<PermissionGroupCatalogEntry>,
    val permissions: List<PermissionCatalogEntry>
)

object PermissionSeedQueries {
    val groupHints = listOf(
        "com.sec.enterprise.permission-group.mdm",
        "android.permission-group.CONTACTS",
        "android.permission-group.PHONE",
        "android.permission-group.CALENDAR",
        "android.permission-group.CALL_LOG",
        "android.permission-group.CAMERA",
        "android.permission-group.HEALTH",
        "android.permission-group.READ_MEDIA_VISUAL",
        "android.permission-group.READ_MEDIA_AURAL",
        "android.permission-group.UNDEFINED",
        "android.permission-group.ACTIVITY_RECOGNITION",
        "android.permission-group.SENSORS",
        "android.permission-group.PERSONAL_INFO",
        "android.permission-group.LOCATION",
        "android.permission-group.STORAGE",
        "com.samsung.android.spay.permission-group.SPAY_SHARE",
        "android.permission-group.NOTIFICATIONS",
        "android.permission-group.MICROPHONE",
        "android.permission-group.NEARBY_DEVICES",
        "android.permission-group.SMS"
    )

    val permissionHints = listOf(
        "com.google.android.gms.permission.CAR_INFORMATION",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.MANAGE_IPSEC_TUNNELS",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.WRITE_SETTINGS",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.ACCESS_NOTIFICATIONS",
        "android.permission.INSTANT_APP_FOREGROUND_SERVICE"
    )
}
