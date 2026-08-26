package io.dpcaio.activity.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import io.dpcaio.activity.ComponentOverrideState

data class ComponentStateReadback(
    val overrideState: ComponentOverrideState,
    val manifestEnabled: Boolean,
    val effectiveEnabled: Boolean
)

data class ComponentStateMutationResult(
    val accepted: Boolean,
    val requestedState: ComponentOverrideState,
    val observedState: ComponentOverrideState?,
    val effectiveEnabled: Boolean?,
    val detail: String
)

data class ComponentStateChange(val component: ComponentName, val state: ComponentOverrideState)
data class ComponentBatchApplyResult(val atomic: Boolean, val results: List<ComponentStateMutationResult>)

class AndroidComponentStateGateway(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    fun readback(component: ComponentName, manifestEnabled: Boolean): ComponentStateReadback {
        val overrideState = pm.getComponentEnabledSetting(component).fromPlatform()
        val effectiveEnabled = when (overrideState) {
            ComponentOverrideState.DEFAULT -> manifestEnabled
            ComponentOverrideState.ENABLED -> true
            ComponentOverrideState.DISABLED -> false
        }
        return ComponentStateReadback(overrideState, manifestEnabled, effectiveEnabled)
    }

    fun setState(component: ComponentName, manifestEnabled: Boolean, requested: ComponentOverrideState, dontKillApp: Boolean = false): ComponentStateMutationResult {
        if (!sameUid(component.packageName)) {
            return ComponentStateMutationResult(false, requested, null, null, "COMPONENT_CONTROL_UNAVAILABLE")
        }
        val flags = if (dontKillApp) PackageManager.DONT_KILL_APP else 0
        return runCatching {
            pm.setComponentEnabledSetting(component, requested.toPlatform(), flags)
            val observed = readback(component, manifestEnabled)
            val ok = observed.overrideState == requested
            ComponentStateMutationResult(ok, requested, observed.overrideState, observed.effectiveEnabled, if (ok) "VERIFIED" else "COMPONENT_STATE_MISMATCH")
        }.getOrElse {
            ComponentStateMutationResult(false, requested, null, null, "${it.javaClass.simpleName}:${it.message}")
        }
    }

    fun setStates(changes: List<ComponentStateChange>, manifestEnabled: Map<ComponentName, Boolean>, dontKillApp: Boolean = false): ComponentBatchApplyResult {
        if (changes.isEmpty()) return ComponentBatchApplyResult(Build.VERSION.SDK_INT >= 33, emptyList())
        val atomic = Build.VERSION.SDK_INT >= 33 && changes.all { sameUid(it.component.packageName) }
        if (!atomic) {
            return ComponentBatchApplyResult(false, changes.map { change ->
                setState(change.component, manifestEnabled[change.component] ?: true, change.state, dontKillApp)
            })
        }
        val flags = if (dontKillApp) PackageManager.DONT_KILL_APP else 0
        val settings = changes.map { change ->
            PackageManager.ComponentEnabledSetting(change.component, change.state.toPlatform(), flags)
        }
        return runCatching {
            pm.setComponentEnabledSettings(settings)
            ComponentBatchApplyResult(true, changes.map { change ->
                val observed = readback(change.component, manifestEnabled[change.component] ?: true)
                val ok = observed.overrideState == change.state
                ComponentStateMutationResult(ok, change.state, observed.overrideState, observed.effectiveEnabled, if (ok) "VERIFIED" else "COMPONENT_STATE_MISMATCH")
            })
        }.getOrElse { error ->
            ComponentBatchApplyResult(true, changes.map { change ->
                ComponentStateMutationResult(false, change.state, null, null, "${error.javaClass.simpleName}:${error.message}")
            })
        }
    }

    private fun sameUid(packageName: String): Boolean = runCatching {
        pm.getApplicationInfo(packageName, 0).uid == Process.myUid()
    }.getOrDefault(false)

    private fun ComponentOverrideState.toPlatform(): Int = when (this) {
        ComponentOverrideState.DEFAULT -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ComponentOverrideState.ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ComponentOverrideState.DISABLED -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun Int.fromPlatform(): ComponentOverrideState = when (this) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> ComponentOverrideState.ENABLED
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> ComponentOverrideState.DISABLED
        else -> ComponentOverrideState.DEFAULT
    }
}
