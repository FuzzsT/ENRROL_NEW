package io.dpcaio.permission

import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.protection.ProtectionDecision

enum class PermissionControlRoute {
    DPC,
    DELEGATED_DPC,
    SHIZUKU,
    SYSTEM_PRIVILEGED,
    USER_ACTION,
    UNAVAILABLE
}

enum class PermissionControlCapability {
    CAN_GRANT_AND_DENY,
    CAN_DENY_AND_DEFAULT,
    SENSOR_GRANT_RESTRICTED,
    PROVISIONING_SENSOR_OPT_OUT,
    USER_ACTION_REQUIRED,
    UNAVAILABLE
}

data class PermissionManagerRecord(
    val packageName: String,
    val permission: String,
    val requestedInManifest: Boolean,
    val actualGranted: Boolean,
    val dpcState: ManagedPermissionState,
    val appOpState: AppOpState?,
    val userId: Int,
    val targetSdk: Int,
    val group: String?,
    val protection: PermissionProtection,
    val route: PermissionControlRoute,
    val capability: PermissionControlCapability
)

data class PermissionControlRequest(
    val desiredState: ManagedPermissionState,
    val isRuntimePermission: Boolean,
    val isSensorPermission: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val delegatedPermissionGrant: Boolean,
    val sensorGrantOptOut: Boolean,
    val shizukuAvailable: Boolean,
    val systemPrivilegedAvailable: Boolean,
    val userActionAvailable: Boolean,
    val targetId: String = "",
    val automated: Boolean = false,
)

data class PermissionControlDecision(
    val route: PermissionControlRoute,
    val capability: PermissionControlCapability,
    val reason: String,
    val protectionDecision: ProtectionDecision = ProtectionDecision.ALLOW,
)
