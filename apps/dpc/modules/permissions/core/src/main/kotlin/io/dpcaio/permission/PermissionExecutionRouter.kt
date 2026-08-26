package io.dpcaio.permission

import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.protection.Mutation
import io.dpcaio.protection.ProtectionDecision
import io.dpcaio.protection.ProtectionPlanner
import io.dpcaio.protection.ProtectionRequest
import io.dpcaio.protection.ProtectedTargetRegistry

class PermissionExecutionRouter(
    private val protectionPlanner: ProtectionPlanner = ProtectionPlanner(ProtectedTargetRegistry.default()),
) {
    fun resolve(request: PermissionControlRequest): PermissionControlDecision {
        val protectionDecision = protectionPlanner.decide(
            ProtectionRequest(
                targetId = request.targetId,
                mutation = if (request.desiredState == ManagedPermissionState.DENIED) Mutation.REVOKE_PERMISSION else Mutation.REVERSIBLE,
                automated = request.automated,
            )
        )
        if (protectionDecision != ProtectionDecision.ALLOW && protectionDecision != ProtectionDecision.ALLOW_WITH_CONFIRMATION) {
            return PermissionControlDecision(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.UNAVAILABLE, protectionDecision.name, protectionDecision)
        }

        fun result(route: PermissionControlRoute, capability: PermissionControlCapability, reason: String) =
            PermissionControlDecision(route, capability, reason, protectionDecision)

        if (!request.isRuntimePermission) {
            return if (request.userActionAvailable) {
                result(PermissionControlRoute.USER_ACTION, PermissionControlCapability.USER_ACTION_REQUIRED, "NOT_RUNTIME_PERMISSION")
            } else {
                result(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.UNAVAILABLE, "NOT_RUNTIME_PERMISSION")
            }
        }

        if (request.desiredState == ManagedPermissionState.GRANTED && request.isSensorPermission) {
            if (request.sensorGrantOptOut) {
                return result(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.PROVISIONING_SENSOR_OPT_OUT, "PROVISIONING_SENSOR_OPT_OUT")
            }
            if (request.isProfileOwner && !request.isDeviceOwner) {
                return if (request.shizukuAvailable) {
                    result(PermissionControlRoute.SHIZUKU, PermissionControlCapability.SENSOR_GRANT_RESTRICTED, "PROFILE_OWNER_SENSOR_GRANT_RESTRICTED")
                } else {
                    result(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.SENSOR_GRANT_RESTRICTED, "PROFILE_OWNER_SENSOR_GRANT_RESTRICTED")
                }
            }
        }

        if (request.isDeviceOwner || request.isProfileOwner) {
            return result(PermissionControlRoute.DPC, PermissionControlCapability.CAN_GRANT_AND_DENY, "DPC_RUNTIME_PERMISSION")
        }
        if (request.delegatedPermissionGrant) {
            return result(PermissionControlRoute.DELEGATED_DPC, PermissionControlCapability.CAN_GRANT_AND_DENY, "DELEGATION_PERMISSION_GRANT")
        }
        if (request.desiredState == ManagedPermissionState.DEFAULT) {
            return result(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.UNAVAILABLE, "DPC_DEFAULT_REQUIRES_DPC")
        }
        if (request.shizukuAvailable) {
            return result(PermissionControlRoute.SHIZUKU, PermissionControlCapability.CAN_GRANT_AND_DENY, "SHIZUKU_AUTHORIZED")
        }
        if (request.systemPrivilegedAvailable) {
            return result(PermissionControlRoute.SYSTEM_PRIVILEGED, PermissionControlCapability.CAN_GRANT_AND_DENY, "SYSTEM_PRIVILEGED")
        }
        if (request.userActionAvailable) {
            return result(PermissionControlRoute.USER_ACTION, PermissionControlCapability.USER_ACTION_REQUIRED, "USER_ACTION_REQUIRED")
        }
        return result(PermissionControlRoute.UNAVAILABLE, PermissionControlCapability.UNAVAILABLE, "NO_CONTROL_ROUTE")
    }
}
