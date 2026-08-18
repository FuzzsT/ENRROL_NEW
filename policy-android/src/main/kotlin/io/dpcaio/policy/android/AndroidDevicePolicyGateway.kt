package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.DelegationPolicyGateway
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

class AndroidDevicePolicyGateway(
    context: Context,
    private val admin: ComponentName
) : DevicePolicyGateway, DelegationPolicyGateway {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)

    override fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyResult<Unit> =
        policyCall {
            if (!dpm.setApplicationHidden(admin, packageName, hidden)) {
                PolicyResult.failure(
                    status = PolicyStatus.PLATFORM_REJECTED,
                    message = "DevicePolicyManager rejected application hidden state for $packageName"
                )
            } else {
                PolicyResult.success()
            }
        }

    override fun isApplicationHidden(packageName: String): PolicyResult<Boolean> =
        try {
            PolicyResult.success(dpm.isApplicationHidden(admin, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            PolicyResult.failure(
                status = PolicyStatus.PACKAGE_NOT_FOUND,
                message = "Package not found: $packageName",
                errorType = e.javaClass.name
            )
        } catch (e: SecurityException) {
            securityFailure(e)
        } catch (e: RuntimeException) {
            runtimeFailure(e)
        }

    override fun setPackagesSuspended(
        packageNames: Set<String>,
        suspended: Boolean
    ): PolicyResult<Set<String>> = policyCall {
        val failures = dpm.setPackagesSuspended(admin, packageNames.toTypedArray(), suspended).toSet()
        PolicyResult.success(
            value = failures,
            message = if (failures.isEmpty()) null else "Some packages could not be changed"
        )
    }


    override fun isPackageSuspended(packageName: String): PolicyResult<Boolean> =
        try {
            PolicyResult.success(dpm.isPackageSuspended(admin, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            PolicyResult.failure(
                status = PolicyStatus.PACKAGE_NOT_FOUND,
                message = "Package not found: $packageName",
                errorType = e.javaClass.name
            )
        } catch (e: SecurityException) {
            securityFailure(e)
        } catch (e: RuntimeException) {
            runtimeFailure(e)
        }

    override fun getDelegatedScopes(packageName: String): PolicyResult<Set<String>> = policyCall {
        PolicyResult.success(dpm.getDelegatedScopes(admin, packageName).toSet())
    }

    override fun setDelegatedScopes(packageName: String, scopes: Set<String>): PolicyResult<Unit> = policyCall {
        dpm.setDelegatedScopes(admin, packageName, scopes.toList())
        PolicyResult.success()
    }

    override fun setPermissionGrantState(
        packageName: String,
        permission: String,
        state: ManagedPermissionState
    ): PolicyResult<Unit> = policyCall {
        val platformState = when (state) {
            ManagedPermissionState.DEFAULT -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            ManagedPermissionState.DENIED -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            ManagedPermissionState.GRANTED -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        }
        if (!dpm.setPermissionGrantState(admin, packageName, permission, platformState)) {
            PolicyResult.failure(
                status = PolicyStatus.PLATFORM_REJECTED,
                message = "DevicePolicyManager rejected permission state for $packageName/$permission"
            )
        } else {
            PolicyResult.success()
        }
    }

    override fun getPermissionGrantState(
        packageName: String,
        permission: String
    ): PolicyResult<ManagedPermissionState> = policyCall {
        val state = when (dpm.getPermissionGrantState(admin, packageName, permission)) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> ManagedPermissionState.GRANTED
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> ManagedPermissionState.DENIED
            else -> ManagedPermissionState.DEFAULT
        }
        PolicyResult.success(state)
    }

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (e: SecurityException) {
        securityFailure(e)
    } catch (e: RuntimeException) {
        runtimeFailure(e)
    }

    private fun <T> securityFailure(e: SecurityException): PolicyResult<T> =
        PolicyResult.failure(
            status = PolicyStatus.SECURITY_EXCEPTION,
            message = e.message ?: "Device policy operation was denied",
            errorType = e.javaClass.name
        )

    private fun <T> runtimeFailure(e: RuntimeException): PolicyResult<T> =
        PolicyResult.failure(
            status = PolicyStatus.FAILED,
            message = e.message ?: "Device policy operation failed",
            errorType = e.javaClass.name
        )
}
