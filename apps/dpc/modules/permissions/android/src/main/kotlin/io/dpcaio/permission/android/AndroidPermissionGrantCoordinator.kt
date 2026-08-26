package io.dpcaio.permission.android

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.dpcaio.permission.PermissionCatalogEntry
import io.dpcaio.permission.PermissionGrantContext
import io.dpcaio.permission.PermissionGrantPlanner
import io.dpcaio.permission.PermissionGrantRoute
import io.dpcaio.permission.PermissionProtection
import io.dpcaio.shizuku.ShizukuUserServiceClient

data class PermissionGrantExecution(
    val permission: String,
    val route: PermissionGrantRoute,
    val verified: Boolean,
    val detail: String
)

interface KnoxSpecialPermissionGateway {
    fun supports(permissionName: String): Boolean
    fun grant(packageName: String, permissionName: String): Boolean
}

class AndroidPermissionGrantCoordinator(
    context: Context,
    private val admin: ComponentName,
    private val shizuku: ShizukuUserServiceClient? = null,
    private val knox: KnoxSpecialPermissionGateway? = null
) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)
    private val planner = PermissionGrantPlanner()

    fun grantAllAuto(packageName: String, userId: Int, entries: List<PermissionCatalogEntry>, systemPrivilegedAvailable: Boolean): List<PermissionGrantExecution> =
        entries.distinctBy { it.name }.map { grantAuto(packageName, userId, it, systemPrivilegedAvailable) }

    fun grantAuto(packageName: String, userId: Int, entry: PermissionCatalogEntry, systemPrivilegedAvailable: Boolean): PermissionGrantExecution {
        val already = pm.checkPermission(entry.name, packageName) == PackageManager.PERMISSION_GRANTED
        val dpcCanGrant = entry.protection == PermissionProtection.DANGEROUS &&
            (dpm.isDeviceOwnerApp(appContext.packageName) || dpm.isProfileOwnerApp(appContext.packageName))
        val special = entry.name in APP_OPS || entry.protection == PermissionProtection.SPECIAL_ACCESS
        val plan = planner.plan(
            PermissionGrantContext(
                entry = entry,
                alreadyGranted = already,
                dpcCanGrantRuntime = dpcCanGrant,
                shizukuAvailable = shizuku?.identity() != null,
                samsungKnoxSpecialAvailable = special && knox?.supports(entry.name) == true,
                systemPrivilegedAvailable = systemPrivilegedAvailable,
                userActionAvailable = special,
                alternateCapabilityRouteAvailable = false
            )
        )
        for (route in plan.routes) {
            val result = execute(route, packageName, userId, entry)
            if (result.verified || route == PermissionGrantRoute.USER_SPECIAL_ACCESS || route == PermissionGrantRoute.SYSTEM_PRIVILEGED) return result
        }
        return PermissionGrantExecution(entry.name, PermissionGrantRoute.BLOCKED, false, "NO_VERIFIED_ROUTE")
    }

    private fun execute(route: PermissionGrantRoute, packageName: String, userId: Int, entry: PermissionCatalogEntry): PermissionGrantExecution = when (route) {
        PermissionGrantRoute.ALREADY_GRANTED -> PermissionGrantExecution(entry.name, route, true, "ALREADY_GRANTED")
        PermissionGrantRoute.DPC_RUNTIME_GRANT -> {
            val accepted = runCatching {
                dpm.setPermissionGrantState(admin, packageName, entry.name, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }.getOrDefault(false)
            PermissionGrantExecution(entry.name, route, accepted && isGranted(packageName, entry.name), "DPC_RUNTIME")
        }
        PermissionGrantRoute.KNOX_SPECIAL_ACCESS -> {
            val accepted = knox?.grant(packageName, entry.name) == true
            PermissionGrantExecution(entry.name, route, accepted && verifySpecial(packageName, entry.name), "KNOX_SPECIAL")
        }
        PermissionGrantRoute.SHIZUKU_RUNTIME_GRANT -> {
            val exit = shizuku?.grantRuntimePermission(packageName, entry.name, userId)
            PermissionGrantExecution(entry.name, route, exit == 0 && isGranted(packageName, entry.name), "SHIZUKU_PM_GRANT:$exit")
        }
        PermissionGrantRoute.SHIZUKU_APPOP -> {
            val pair = APP_OPS[entry.name]
            if (pair == null) PermissionGrantExecution(entry.name, route, false, "NO_APPOP_MAPPING")
            else {
                val exit = shizuku?.setAppOp(packageName, pair.commandName, "allow", userId)
                PermissionGrantExecution(entry.name, route, exit == 0 && verifyAppOp(packageName, pair.opStr), "SHIZUKU_APPOP:$exit")
            }
        }
        PermissionGrantRoute.SYSTEM_PRIVILEGED -> PermissionGrantExecution(
            entry.name, route, isGranted(packageName, entry.name),
            if (isGranted(packageName, entry.name)) "SYSTEM_GRANT_PRESENT" else "SYSTEM_IMAGE_OR_SIGNATURE_REQUIRED"
        )
        PermissionGrantRoute.USER_SPECIAL_ACCESS -> PermissionGrantExecution(entry.name, route, false, "USER_SETTINGS_REQUIRED")
        PermissionGrantRoute.ALTERNATE_CAPABILITY -> PermissionGrantExecution(entry.name, route, false, "EFFECTIVE_CAPABILITY_ONLY")
        PermissionGrantRoute.LAB_HOOK_SIMULATION -> PermissionGrantExecution(entry.name, route, false, "LAB_SIMULATION_NOT_A_GRANT")
        PermissionGrantRoute.BLOCKED -> PermissionGrantExecution(entry.name, route, false, "BLOCKED")
    }

    private fun isGranted(packageName: String, permission: String) = pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED

    private fun verifySpecial(packageName: String, permission: String): Boolean {
        val pair = APP_OPS[permission] ?: return isGranted(packageName, permission)
        return verifyAppOp(packageName, pair.opStr)
    }

    private fun verifyAppOp(packageName: String, op: String): Boolean = runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        appOps.checkOpNoThrow(op, info.uid, packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private data class AppOpMapping(val commandName: String, val opStr: String)

    companion object {
        private val APP_OPS = mapOf(
            "android.permission.SYSTEM_ALERT_WINDOW" to AppOpMapping("SYSTEM_ALERT_WINDOW", "android:system_alert_window"),
            "android.permission.WRITE_SETTINGS" to AppOpMapping("WRITE_SETTINGS", "android:write_settings"),
            "android.permission.REQUEST_INSTALL_PACKAGES" to AppOpMapping("REQUEST_INSTALL_PACKAGES", "android:request_install_packages"),
            "android.permission.PACKAGE_USAGE_STATS" to AppOpMapping("GET_USAGE_STATS", AppOpsManager.OPSTR_GET_USAGE_STATS)
        )
    }
}
