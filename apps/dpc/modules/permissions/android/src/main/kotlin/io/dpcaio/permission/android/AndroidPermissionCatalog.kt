package io.dpcaio.permission.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import io.dpcaio.permission.PermissionCatalogEntry
import io.dpcaio.permission.PermissionCatalogSnapshot
import io.dpcaio.permission.PermissionGroupCatalogEntry
import io.dpcaio.permission.PermissionProtection

class AndroidPermissionCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    @Suppress("DEPRECATION")
    fun scan(): PermissionCatalogSnapshot {
        val publicPermissions = Manifest.permission::class.java.fields.mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }.toSet()
        val publicGroups = Manifest.permission_group::class.java.fields.mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }.toSet()
        val permissions = linkedMapOf<String, PermissionCatalogEntry>()

        fun add(info: PermissionInfo) {
            permissions[info.name] = PermissionCatalogEntry(
                name = info.name,
                group = info.group,
                declaringPackage = info.packageName,
                protection = protectionOf(info),
                publicSdkConstant = info.name in publicPermissions,
                description = runCatching { info.loadDescription(pm)?.toString() }.getOrNull(),
                rawProtectionLevel = info.protectionLevel,
                permissionFlags = info.flags
            )
        }

        val groupInfos = runCatching { pm.getAllPermissionGroups(PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        groupInfos.forEach { group ->
            runCatching { pm.queryPermissionsByGroup(group.name, PackageManager.GET_META_DATA) }
                .getOrDefault(emptyList()).forEach(::add)
        }
        runCatching { pm.queryPermissionsByGroup(null, PackageManager.GET_META_DATA) }
            .getOrDefault(emptyList()).forEach(::add)

        // PackageInfo.permissions exposes permissions declared by installed packages, including vendor/OEM permissions.
        runCatching { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) }.getOrDefault(emptyList()).forEach { pkg ->
            pkg.permissions?.forEach(::add)
        }

        val groups = groupInfos.map { group ->
            PermissionGroupCatalogEntry(
                name = group.name,
                declaringPackage = group.packageName,
                publicSdkConstant = group.name in publicGroups,
                description = runCatching { group.loadDescription(pm)?.toString() }.getOrNull()
            )
        }.sortedBy { it.name }

        return PermissionCatalogSnapshot(groups, permissions.values.sortedBy { it.name })
    }

    private fun protectionOf(info: PermissionInfo): PermissionProtection {
        if (info.name in SPECIAL_ACCESS) return PermissionProtection.SPECIAL_ACCESS
        val base = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        val flags = info.protectionLevel and PermissionInfo.PROTECTION_MASK_FLAGS
        val privileged = flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0
        val appOp = flags and PermissionInfo.PROTECTION_FLAG_APPOP != 0
        if (base == PermissionInfo.PROTECTION_SIGNATURE && privileged) return PermissionProtection.SIGNATURE_PRIVILEGED
        if (base == PermissionInfo.PROTECTION_SIGNATURE) return PermissionProtection.SIGNATURE
        if (base == PermissionInfo.PROTECTION_DANGEROUS) return PermissionProtection.DANGEROUS
        if (base == PermissionInfo.PROTECTION_NORMAL) return if (appOp) PermissionProtection.APPOP else PermissionProtection.NORMAL
        return if (appOp) PermissionProtection.APPOP else PermissionProtection.UNKNOWN
    }

    companion object {
        private val SPECIAL_ACCESS = setOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.WRITE_SETTINGS",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.ACCESS_NOTIFICATIONS"
        )
    }
}
