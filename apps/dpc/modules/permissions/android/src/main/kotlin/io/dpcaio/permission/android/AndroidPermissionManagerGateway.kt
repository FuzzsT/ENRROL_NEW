package io.dpcaio.permission.android

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import io.dpcaio.permission.AppOpState
import io.dpcaio.permission.PermissionControlCapability
import io.dpcaio.permission.PermissionControlDecision
import io.dpcaio.permission.PermissionControlRequest
import io.dpcaio.permission.PermissionControlRoute
import io.dpcaio.permission.PermissionExecutionRouter
import io.dpcaio.permission.PermissionManagerRecord
import io.dpcaio.permission.PermissionProtection
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.policy.ManagedPermissionState

enum class GlobalRuntimePermissionPolicy { PROMPT, AUTO_GRANT, AUTO_DENY }

data class PermissionMutationResult(
    val accepted: Boolean,
    val requestedState: ManagedPermissionState,
    val observedDpcState: ManagedPermissionState?,
    val actualGranted: Boolean?,
    val detail: String
)

class AndroidPermissionManagerGateway(
    context: Context,
    private val admin: ComponentName
) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)
    private val router = PermissionExecutionRouter()

    fun inspectPermission(
        packageName: String,
        permission: String,
        targetUserId: Int,
        group: String? = null,
        protection: PermissionProtection = PermissionProtection.UNKNOWN,
        sensorGrantOptOut: Boolean = false,
        shizukuAvailable: Boolean = false,
        systemPrivilegedAvailable: Boolean = false,
        userActionAvailable: Boolean = false
    ): PermissionManagerRecord {
        val currentUserId = AndroidUserId.fromUid(Process.myUid())
        val requested = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains(permission) == true
        }.getOrDefault(false)
        val actualGranted = targetUserId == currentUserId && pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
        val dpcState = if (targetUserId == currentUserId) readDpcState(packageName, permission) else ManagedPermissionState.DEFAULT
        val appOp = if (targetUserId == currentUserId) readAppOp(packageName, permission) else null
        val targetSdk = runCatching { pm.getApplicationInfo(packageName, 0).targetSdkVersion }.getOrDefault(0)
        val decision = resolveDecision(permission, targetUserId, ManagedPermissionState.GRANTED, protection, sensorGrantOptOut, shizukuAvailable, systemPrivilegedAvailable, userActionAvailable, packageName = packageName)
        return PermissionManagerRecord(
            packageName = packageName,
            permission = permission,
            requestedInManifest = requested,
            actualGranted = actualGranted,
            dpcState = dpcState,
            appOpState = appOp,
            userId = targetUserId,
            targetSdk = targetSdk,
            group = group,
            protection = protection,
            route = decision.route,
            capability = decision.capability
        )
    }

    fun resolveDecision(
        permission: String,
        targetUserId: Int,
        desiredState: ManagedPermissionState,
        protection: PermissionProtection,
        sensorGrantOptOut: Boolean,
        shizukuAvailable: Boolean,
        systemPrivilegedAvailable: Boolean,
        userActionAvailable: Boolean,
        packageName: String = "",
        automated: Boolean = false,
    ): PermissionControlDecision {
        val currentUserId = AndroidUserId.fromUid(Process.myUid())
        val ownerAvailable = targetUserId == currentUserId &&
            (dpm.isDeviceOwnerApp(appContext.packageName) || dpm.isProfileOwnerApp(appContext.packageName))
        val isDeviceOwner = targetUserId == currentUserId && dpm.isDeviceOwnerApp(appContext.packageName)
        val isProfileOwner = targetUserId == currentUserId && dpm.isProfileOwnerApp(appContext.packageName)
        return router.resolve(
            PermissionControlRequest(
                desiredState = desiredState,
                isRuntimePermission = protection == PermissionProtection.DANGEROUS,
                isSensorPermission = permission in SENSOR_PERMISSIONS,
                isDeviceOwner = isDeviceOwner,
                isProfileOwner = isProfileOwner && ownerAvailable,
                delegatedPermissionGrant = false,
                sensorGrantOptOut = sensorGrantOptOut,
                shizukuAvailable = shizukuAvailable,
                systemPrivilegedAvailable = systemPrivilegedAvailable,
                userActionAvailable = userActionAvailable,
                targetId = packageName,
                automated = automated,
            )
        )
    }

    fun setDpcPermissionState(
        packageName: String,
        permission: String,
        targetUserId: Int,
        state: ManagedPermissionState
    ): PermissionMutationResult {
        val currentUserId = AndroidUserId.fromUid(Process.myUid())
        if (targetUserId != currentUserId) {
            return PermissionMutationResult(false, state, null, null, "TARGET_USER_UNAVAILABLE")
        }
        val accepted = runCatching {
            dpm.setPermissionGrantState(admin, packageName, permission, state.toPlatform())
        }.getOrDefault(false)
        val observed = runCatching { readDpcState(packageName, permission) }.getOrNull()
        val actual = runCatching { pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED }.getOrNull()
        val verified = accepted && observed == state
        return PermissionMutationResult(
            accepted = verified,
            requestedState = state,
            observedDpcState = observed,
            actualGranted = actual,
            detail = if (verified) "VERIFIED" else "POLICY_READBACK_MISMATCH"
        )
    }

    fun getGlobalPermissionPolicy(): GlobalRuntimePermissionPolicy = when (dpm.getPermissionPolicy(admin)) {
        DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT -> GlobalRuntimePermissionPolicy.AUTO_GRANT
        DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY -> GlobalRuntimePermissionPolicy.AUTO_DENY
        else -> GlobalRuntimePermissionPolicy.PROMPT
    }

    fun setGlobalPermissionPolicy(policy: GlobalRuntimePermissionPolicy): Boolean {
        val platform = when (policy) {
            GlobalRuntimePermissionPolicy.PROMPT -> DevicePolicyManager.PERMISSION_POLICY_PROMPT
            GlobalRuntimePermissionPolicy.AUTO_GRANT -> DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
            GlobalRuntimePermissionPolicy.AUTO_DENY -> DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY
        }
        return runCatching {
            dpm.setPermissionPolicy(admin, platform)
            dpm.getPermissionPolicy(admin) == platform
        }.getOrDefault(false)
    }

    private fun readDpcState(packageName: String, permission: String): ManagedPermissionState = when (
        dpm.getPermissionGrantState(admin, packageName, permission)
    ) {
        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> ManagedPermissionState.GRANTED
        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> ManagedPermissionState.DENIED
        else -> ManagedPermissionState.DEFAULT
    }

    private fun readAppOp(packageName: String, permission: String): AppOpState? {
        val op = AppOpsManager.permissionToOp(permission) ?: return null
        val uid = runCatching { pm.getApplicationInfo(packageName, 0).uid }.getOrNull() ?: return null
        return when (appOps.checkOpNoThrow(op, uid, packageName)) {
            AppOpsManager.MODE_ALLOWED -> AppOpState.ALLOWED
            AppOpsManager.MODE_IGNORED -> AppOpState.IGNORED
            AppOpsManager.MODE_ERRORED -> AppOpState.ERRORED
            AppOpsManager.MODE_DEFAULT -> AppOpState.DEFAULT
            AppOpsManager.MODE_FOREGROUND -> AppOpState.FOREGROUND
            else -> AppOpState.UNKNOWN
        }
    }

    private fun ManagedPermissionState.toPlatform(): Int = when (this) {
        ManagedPermissionState.DEFAULT -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        ManagedPermissionState.DENIED -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        ManagedPermissionState.GRANTED -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
    }

    companion object {
        private val SENSOR_PERMISSIONS = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION"
        )
    }
}
