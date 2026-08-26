package io.dpcaio.lab

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

enum class LabHookKind { JAVA, ART }

data class LabHookLaunchResult(val hookInstalled: Boolean, val launched: Boolean, val detail: String)

fun interface LabHookInstaller { fun install(kind: LabHookKind): Boolean }

class LabHookActivityBridge(
    private val context: Context,
    private val hookInstaller: LabHookInstaller
) {
    fun installThenLaunch(kind: LabHookKind, packageName: String, className: String): LabHookLaunchResult {
        if (packageName != context.packageName) return LabHookLaunchResult(false, false, "OWN_PACKAGE_ONLY")
        val info = context.applicationInfo
        if (info.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return LabHookLaunchResult(false, false, "DEBUGGABLE_REQUIRED")
        if (!hookInstaller.install(kind)) return LabHookLaunchResult(false, false, "HOOK_NOT_INSTALLED")
        return runCatching {
            context.startActivity(Intent().setComponent(ComponentName(packageName, className)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LabHookLaunchResult(true, true, "LAUNCHED_AFTER_${kind.name}_HOOK")
        }.getOrElse { LabHookLaunchResult(true, false, it.javaClass.simpleName) }
    }
}
