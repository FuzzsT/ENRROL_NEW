package io.dpcaio.delegation

enum class DelegatedOperation(val requiredScope: String) {
    PACKAGE_READ("packages.read"),
    PACKAGE_VISIBILITY("packages.visibility"),
    PACKAGE_SUSPEND("packages.suspend"),
    APP_INSTALL("apps.install"),
    APP_UNINSTALL("apps.uninstall"),
    PERMISSION_READ("permissions.read"),
    PERMISSION_MANAGE("permissions.runtime.manage"),
    ACTIVITY_READ("activities.read"),
    ACTIVITY_LAUNCH("activities.launch"),
    BROADCAST_SEND("broadcast.send"),
    DEVICE_STATUS("device.status"),
    DEVICE_RESTRICTIONS("device.restrictions"),
    NETWORK_READ("network.read"),
    NETWORK_MANAGE("network.manage")
}

data class DelegatedRequest(
    val caller: ClientIdentity,
    val operation: DelegatedOperation,
    val targetPackage: String? = null,
    val targetComponent: String? = null,
    val arguments: Map<String, String> = emptyMap()
)

data class DelegatedExecutionResult(
    val success: Boolean,
    val detail: String? = null
)

fun interface DelegatedOperationExecutor {
    fun execute(request: DelegatedRequest): DelegatedExecutionResult
}

enum class DelegationResultCode {
    EXECUTED,
    AUTHORIZATION_DENIED,
    EXECUTION_FAILED
}

data class DelegatedResult(
    val success: Boolean,
    val code: DelegationResultCode,
    val authorizationReason: AuthorizationReason? = null,
    val detail: String? = null
)

class DelegationBroker(
    private val authorizer: DelegationAuthorizer,
    private val executor: DelegatedOperationExecutor
) {
    fun execute(request: DelegatedRequest): DelegatedResult {
        val auth = authorizer.authorize(request.caller, request.operation.requiredScope)
        if (!auth.allowed) {
            return DelegatedResult(
                success = false,
                code = DelegationResultCode.AUTHORIZATION_DENIED,
                authorizationReason = auth.reason
            )
        }

        val execution = executor.execute(request)
        return if (execution.success) {
            DelegatedResult(true, DelegationResultCode.EXECUTED, AuthorizationReason.ALLOWED, execution.detail)
        } else {
            DelegatedResult(false, DelegationResultCode.EXECUTION_FAILED, AuthorizationReason.ALLOWED, execution.detail)
        }
    }
}
