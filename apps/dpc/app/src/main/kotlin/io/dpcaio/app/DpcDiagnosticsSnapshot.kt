package io.dpcaio.app

import android.content.ComponentName
import android.content.Context
import android.os.Build
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.VisibilityClass
import io.dpcaio.policy.android.AndroidDevicePolicyGateway
import io.dpcaio.samsung.firmware.SamsungFirmwareProfile
import io.dpcaio.samsung.firmware.android.AndroidSamsungFirmwareProbe
import io.dpcaio.shizuku.AndroidShizukuRuntime
import org.json.JSONArray
import org.json.JSONObject

data class DpcDiagnosticsSnapshot(
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val dpcVersion: String,
    val managementState: ManagementDiagnosticState,
    val ownerPolicyReady: Boolean,
    val moduleAvailabilitySemantics: String,
    val deviceOwner: Boolean,
    val profileOwner: Boolean,
    val organizationOwnedProfile: Boolean,
    val affiliatedUser: Boolean,
    val samsungDevice: Boolean,
    val knoxAvailable: Boolean,
    val knoxLicenseActive: Boolean,
    val samsungFirmware: SamsungFirmwareProfile,
    val shizukuBinderAlive: Boolean,
    val shizukuPermissionGranted: Boolean,
    val dhizukuCompiled: Boolean,
    val securityLoggingEnabled: Boolean?,
    val networkLoggingEnabled: Boolean?,
    val pendingNetworkBatchToken: Long?,
    val securityLogsPending: Boolean,
    val systemUpdateMode: String?,
    val freezePeriodCount: Int?,
    val caCertificateCount: Int?,
    val crossProfilePackageCount: Int?,
    val managedProfileMaximumTimeOffMillis: Long?,
    val offlineBundleId: String?,
    val offlineStage: String?,
    val offlineSyncPending: Boolean,
    val offlineLastError: String?,
    val moduleCounts: ModuleCounts,
) {
    data class ModuleCounts(
        val integrated: Int,
        val visible: Int,
        val hidden: Int,
        val available: Int,
        val unavailable: Int,
        val lab: Int,
    )

    fun toJson(): String = JSONObject().apply {
        put("apiLevel", apiLevel)
        put("manufacturer", manufacturer)
        put("model", model)
        put("dpcVersion", dpcVersion)
        put("managementState", managementState.name)
        put("ownerPolicyReady", ownerPolicyReady)
        put("moduleAvailabilitySemantics", moduleAvailabilitySemantics)
        put("deviceOwner", deviceOwner)
        put("profileOwner", profileOwner)
        put("organizationOwnedProfile", organizationOwnedProfile)
        put("affiliatedUser", affiliatedUser)
        put("samsungDevice", samsungDevice)
        put("knoxAvailable", knoxAvailable)
        put("knoxLicenseActive", knoxLicenseActive)
        put("samsungFirmware", JSONObject().apply {
            put("samsungDevice", samsungFirmware.samsungDevice)
            putNullable("salesCode", samsungFirmware.salesCode)
            putNullable("multiCsc", samsungFirmware.multiCsc)
            putNullable("countryIso", samsungFirmware.countryIso)
            putNullable("omcPath", samsungFirmware.omcPath)
            putNullable("omcEtcPath", samsungFirmware.omcEtcPath)
            putNullable("omcBuildVersion", samsungFirmware.omcBuildVersion)
            putNullable("buildPda", samsungFirmware.buildPda)
            putNullable("buildIncremental", samsungFirmware.buildIncremental)
            put("propertyAccessAvailable", samsungFirmware.propertyAccessAvailable)
            put("observedPackageCount", samsungFirmware.observedPackageCount)
            put("packages", JSONArray().apply {
                samsungFirmware.packages.forEach { probe ->
                    put(JSONObject().apply {
                        put("packageName", probe.packageName)
                        put("role", probe.role)
                        put("installed", probe.installed)
                        putNullable("enabled", probe.enabled)
                        putNullable("systemApp", probe.systemApp)
                    })
                }
            })
        })
        put("shizukuBinderAlive", shizukuBinderAlive)
        put("shizukuPermissionGranted", shizukuPermissionGranted)
        put("dhizukuCompiled", dhizukuCompiled)
        putNullable("securityLoggingEnabled", securityLoggingEnabled)
        putNullable("networkLoggingEnabled", networkLoggingEnabled)
        putNullable("pendingNetworkBatchToken", pendingNetworkBatchToken)
        put("securityLogsPending", securityLogsPending)
        putNullable("systemUpdateMode", systemUpdateMode)
        putNullable("freezePeriodCount", freezePeriodCount)
        putNullable("caCertificateCount", caCertificateCount)
        putNullable("crossProfilePackageCount", crossProfilePackageCount)
        putNullable("managedProfileMaximumTimeOffMillis", managedProfileMaximumTimeOffMillis)
        putNullable("offlineBundleId", offlineBundleId)
        putNullable("offlineStage", offlineStage)
        put("offlineSyncPending", offlineSyncPending)
        putNullable("offlineLastError", offlineLastError)
        put("moduleCounts", JSONObject().apply {
            put("integrated", moduleCounts.integrated)
            put("visible", moduleCounts.visible)
            put("hidden", moduleCounts.hidden)
            put("available", moduleCounts.available)
            put("unavailable", moduleCounts.unavailable)
            put("lab", moduleCounts.lab)
        })
    }.toString(2)

    companion object {
        private fun JSONObject.putNullable(key: String, value: Any?) {
            put(key, value ?: JSONObject.NULL)
        }

        fun capture(context: Context): DpcDiagnosticsSnapshot {
            val management = ManagementContextFactory.create(context)
            val resolutions = DpcModuleRegistry.modules.map { module ->
                module to CapabilityResolver.resolve(module.requirements, management)
            }
            val shizuku = runCatching { AndroidShizukuRuntime().probe() }.getOrNull()
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")
            val gateway = AndroidDevicePolicyGateway(context, ComponentName(context, AioDeviceAdminReceiver::class.java))
            val logState = EnterpriseLogStateStore(context)
            val update = gateway.getSystemUpdatePolicySpec().value
            val cope = gateway.getCopePolicySnapshot().value
            val offline = OfflineDeploymentStore(context).load()
            val samsungFirmware = AndroidSamsungFirmwareProbe(context).read()

            val managementState = when {
                management.ownership == io.dpcaio.core.model.OwnershipMode.DEVICE_OWNER -> ManagementDiagnosticState.DEVICE_OWNER
                management.ownership == io.dpcaio.core.model.OwnershipMode.PROFILE_OWNER && management.organizationOwnedProfile -> ManagementDiagnosticState.ORGANIZATION_OWNED_PROFILE
                management.ownership == io.dpcaio.core.model.OwnershipMode.PROFILE_OWNER -> ManagementDiagnosticState.PROFILE_OWNER
                else -> ManagementDiagnosticState.UNMANAGED
            }

            return DpcDiagnosticsSnapshot(
                apiLevel = management.apiLevel,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                dpcVersion = version,
                managementState = managementState,
                ownerPolicyReady = managementState != ManagementDiagnosticState.UNMANAGED,
                moduleAvailabilitySemantics = "MODULE_SURFACE_EXECUTABLE_NOT_POLICY_ACTION_READINESS",
                deviceOwner = management.ownership == io.dpcaio.core.model.OwnershipMode.DEVICE_OWNER,
                profileOwner = management.ownership == io.dpcaio.core.model.OwnershipMode.PROFILE_OWNER,
                organizationOwnedProfile = management.organizationOwnedProfile,
                affiliatedUser = management.affiliatedUser,
                samsungDevice = management.samsungDevice,
                knoxAvailable = management.knoxAvailable,
                knoxLicenseActive = management.knoxLicenseActive,
                samsungFirmware = samsungFirmware,
                shizukuBinderAlive = shizuku?.binderAlive ?: false,
                shizukuPermissionGranted = shizuku?.permissionGranted ?: false,
                dhizukuCompiled = runCatching { Class.forName("io.dpcaio.delegation.dhizuku.DhizukuCompatRuntime") }.isSuccess,
                securityLoggingEnabled = gateway.isSecurityLoggingEnabled().value,
                networkLoggingEnabled = gateway.isNetworkLoggingEnabled().value,
                pendingNetworkBatchToken = logState.networkBatchToken(),
                securityLogsPending = logState.securityLogsAvailable(),
                systemUpdateMode = update?.mode?.name,
                freezePeriodCount = update?.freezePeriods?.size,
                caCertificateCount = gateway.getInstalledCaCertificates().value?.size,
                crossProfilePackageCount = cope?.crossProfilePackages?.size,
                managedProfileMaximumTimeOffMillis = cope?.maximumTimeOffMillis,
                offlineBundleId = offline?.bundleId,
                offlineStage = offline?.stage?.name,
                offlineSyncPending = offline?.syncPending ?: false,
                offlineLastError = offline?.lastError,
                moduleCounts = ModuleCounts(
                    integrated = resolutions.size,
                    visible = resolutions.count { it.second.visible },
                    hidden = resolutions.count { !it.second.visible },
                    available = resolutions.count { it.second.executable },
                    unavailable = resolutions.count { !it.second.executable },
                    lab = resolutions.count { it.first.requirements.visibility == VisibilityClass.LAB },
                ),
            )
        }
    }
}
