package io.dpcaio.policy.android.parity

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

class AndroidAppParityGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val packageInstaller = appContext.packageManager.packageInstaller

    fun enableSystemApp(packageName: String): PolicyResult<Unit> = policyCall {
        dpm.enableSystemApp(admin, packageName)
        PolicyResult.success(message = "System app enabled: $packageName")
    }

    fun enableSystemAppsByIntent(intent: Intent): PolicyResult<Int> = policyCall {
        val count = dpm.enableSystemApp(admin, intent)
        PolicyResult.success(count, "Enabled $count matching system app(s)")
    }

    fun installExistingPackage(packageName: String): PolicyResult<String> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Install existing package requires API 28+")
        return policyCall {
            if (!dpm.installExistingPackage(admin, packageName)) {
                PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "DevicePolicyManager rejected install-existing request for $packageName",
                )
            } else {
                PolicyResult.success(packageName, "Existing package installed")
            }
        }
    }

    fun setKeepUninstalledPackages(packages: List<String>): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Keep-uninstalled packages requires API 28+")
        return policyCall {
            dpm.setKeepUninstalledPackages(admin, packages)
            PolicyResult.success(message = "Keep-uninstalled package policy updated")
        }
    }

    fun setMeteredDataDisabledPackages(packages: List<String>): PolicyResult<List<String>> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Metered-data package policy requires API 28+")
        return policyCall {
            val failures = dpm.setMeteredDataDisabledPackages(admin, packages)
            PolicyResult.success(
                failures,
                if (failures.isEmpty()) "Metered-data policy applied" else "Some packages were rejected",
            )
        }
    }

    fun uninstallPackage(packageName: String): PolicyResult<Unit> = policyCall {
        val callback = PendingIntent.getBroadcast(
            appContext,
            packageName.hashCode(),
            Intent(ACTION_UNINSTALL_RESULT).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        packageInstaller.uninstall(packageName, callback.intentSender)
        PolicyResult.success(message = "Package uninstall request submitted: $packageName")
    }

    fun setApplicationRestrictionsManagingPackage(packageName: String?): PolicyResult<Unit> = policyCall {
        if (!dpm.setApplicationRestrictionsManagingPackage(admin, packageName)) {
            PolicyResult.failure(
                PolicyStatus.PLATFORM_REJECTED,
                "DevicePolicyManager rejected application restrictions manager",
            )
        } else {
            PolicyResult.success(message = "Application restrictions manager updated")
        }
    }

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (error: SecurityException) {
        PolicyResult.failure(
            PolicyStatus.SECURITY_EXCEPTION,
            error.message ?: "SECURITY_EXCEPTION",
            error.javaClass.name,
        )
    } catch (error: IllegalArgumentException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: "INVALID_ARGUMENT",
            error.javaClass.name,
        )
    } catch (error: RuntimeException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: error.javaClass.simpleName,
            error.javaClass.name,
        )
    }

    private fun <T> unsupported(message: String): PolicyResult<T> =
        PolicyResult.failure(PolicyStatus.UNSUPPORTED, message)

    private companion object {
        const val ACTION_UNINSTALL_RESULT = "io.dpcaio.app.PARITY_UNINSTALL_RESULT"
    }
}
