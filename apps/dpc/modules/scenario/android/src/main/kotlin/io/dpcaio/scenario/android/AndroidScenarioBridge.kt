package io.dpcaio.scenario.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import io.dpcaio.scenario.ScenarioArchiveCodec
import io.dpcaio.scenario.ScenarioEvent
import io.dpcaio.scenario.ScenarioEventType
import io.dpcaio.scenario.ScenarioRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class AndroidScenarioBridge(private val recorder: ScenarioRecorder = ScenarioRecorder()) : Application.ActivityLifecycleCallbacks {
    private val sequence = AtomicLong(1)

    fun install(application: Application) = application.registerActivityLifecycleCallbacks(this)
    fun uninstall(application: Application) = application.unregisterActivityLifecycleCallbacks(this)

    fun recordIntent(intent: Intent, marker: String = "intent") = record(
        ScenarioEventType.INTENT,
        marker,
        "action=${intent.action};component=${intent.component};data=${intent.data}"
    )

    fun recordBroadcast(intent: Intent) = recordIntent(intent, "broadcast").also {
        record(ScenarioEventType.BROADCAST, intent.action ?: "broadcast", "component=${intent.component};data=${intent.data}")
    }

    fun recordPolicy(name: String, payload: String) = record(ScenarioEventType.POLICY, name, payload)
    fun recordPermission(name: String, payload: String) = record(ScenarioEventType.PERMISSION, name, payload)
    fun recordNetwork(name: String, payload: String) = record(ScenarioEventType.NETWORK, name, payload)
    fun recordNfc(name: String, payload: String) = record(ScenarioEventType.NFC, name, payload)
    fun recordNative(name: String, payload: String) = record(ScenarioEventType.NATIVE, name, payload)

    fun snapshot(): List<ScenarioEvent> = recorder.snapshot()
    fun clear() = recorder.clear()

    fun exportTo(file: File) { file.writeText(ScenarioArchiveCodec.encode(snapshot())) }
    fun importFrom(file: File): List<ScenarioEvent> = ScenarioArchiveCodec.decode(file.readText())

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = lifecycle(activity, "created")
    override fun onActivityStarted(activity: Activity) = lifecycle(activity, "started")
    override fun onActivityResumed(activity: Activity) = lifecycle(activity, "resumed")
    override fun onActivityPaused(activity: Activity) = lifecycle(activity, "paused")
    override fun onActivityStopped(activity: Activity) = lifecycle(activity, "stopped")
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = lifecycle(activity, "save_state")
    override fun onActivityDestroyed(activity: Activity) = lifecycle(activity, "destroyed")

    private fun lifecycle(activity: Activity, state: String) = record(
        ScenarioEventType.ACTIVITY,
        activity.componentName.flattenToShortString(),
        state
    )

    private fun record(type: ScenarioEventType, name: String, payload: String) {
        recorder.record(
            ScenarioEvent(
                sequence = sequence.getAndIncrement(),
                monotonicNanos = SystemClock.elapsedRealtimeNanos(),
                type = type,
                name = name,
                payload = payload
            )
        )
    }
}
