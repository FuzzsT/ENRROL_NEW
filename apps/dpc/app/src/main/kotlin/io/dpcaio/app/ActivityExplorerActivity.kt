package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.activity.ActivityAccessInput
import io.dpcaio.activity.ActivityAccessPlanner
import io.dpcaio.activity.ActivityExecutorRouter
import io.dpcaio.activity.ActivityLaunchCoordinator
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.ComponentBatchPlanner
import io.dpcaio.activity.ComponentControlDecision
import io.dpcaio.activity.ComponentControlRequest
import io.dpcaio.activity.ComponentControlRoute
import io.dpcaio.activity.ComponentControlRouter
import io.dpcaio.activity.ComponentControlStatus
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.DiscoveredActivity
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.activity.android.AndroidActivityRouteExecutor
import io.dpcaio.activity.android.AndroidComponentStateGateway
import io.dpcaio.activity.android.ComponentStateChange
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import io.dpcaio.knoxzt.android.KnoxZtRecoveryManager
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.AndroidShizukuRuntime
import io.dpcaio.shizuku.ShizukuActivityRouteExecutor
import io.dpcaio.shizuku.ShizukuComponentStateExecutor
import io.dpcaio.shizuku.ShizukuUserServiceClient

class ActivityExplorerActivity : Activity() {
    private lateinit var packageNameInput: EditText
    private lateinit var targetUserInput: EditText
    private lateinit var filterInput: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val stateGateway by lazy { AndroidComponentStateGateway(this) }
    private val componentRouter = ComponentControlRouter()
    private val snapshotStore by lazy { ComponentStateSnapshotStore(this) }
    private var currentActivities: List<DiscoveredActivity> = emptyList()
    private var pendingBatch: List<Pair<DiscoveredActivity, ComponentControlDecision>> = emptyList()
    private var pendingBatchState: ComponentOverrideState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Activity Manager 2.0 / Activity Explorer"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        packageNameInput = EditText(this).apply { hint = "package name"; setText(intent.getStringExtra("package") ?: KNOXZT_PACKAGE) }
        targetUserInput = EditText(this).apply {
            hint = "Target user ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(AndroidUserId.fromUid(Process.myUid()).toString())
        }
        filterInput = EditText(this).apply { hint = "filter class / enabled / disabled" }
        status = TextView(this).apply { setTextIsSelectable(true) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(packageNameInput)
        root.addView(targetUserInput)
        root.addView(filterInput)
        root.addView(Button(this).apply { text = "Scan activities"; setOnClickListener { scan() } })
        root.addView(TextView(this).apply { text = "Batch Preview" })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Batch Enable") { batchPreviewFiltered(ComponentOverrideState.ENABLED) })
            addView(button("Batch Disable") { batchPreviewFiltered(ComponentOverrideState.DISABLED) })
            addView(button("Batch Restore Default") { batchPreviewFiltered(ComponentOverrideState.DEFAULT) })
        })
        root.addView(Button(this).apply { text = "Apply batch"; setOnClickListener { applyBatch() } })
        root.addView(Button(this).apply { text = "Restore snapshot"; setOnClickListener { restoreSnapshot() } })
        root.addView(status)
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(root)
        shizuku.bind()
    }

    private fun targetUserId(): Int = targetUserInput.text.toString().trim().toIntOrNull() ?: AndroidUserId.fromUid(Process.myUid())

    private fun scan() {
        val pkg = packageNameInput.text.toString().trim()
        if (pkg.isBlank()) return
        val targetUserId = targetUserId()
        status.text = "Scanning $pkg for user $targetUserId..."
        Thread {
            if (pkg == KNOXZT_PACKAGE) {
                KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = shizuku).ensureReady()
            }
            val activities = runCatching { AndroidActivityInventory(this).list(pkg, UserHandle.of(targetUserId)) }.getOrElse { emptyList() }
            currentActivities = activities
            runOnUiThread { renderActivities(activities) }
        }.start()
    }

    private fun renderActivities(activities: List<DiscoveredActivity>) {
        list.removeAllViews()
        val filter = filterInput.text.toString().trim().lowercase()
        val shown = activities.filter { a ->
            filter.isBlank() || a.className.lowercase().contains(filter) ||
                (filter == "enabled" && a.effectiveEnabled) || (filter == "disabled" && !a.effectiveEnabled)
        }
        status.text = "Activity Manager 2.0\nactivities=${activities.size} shown=${shown.size}\nTarget user ID=${targetUserId()}"
        shown.forEach { activity ->
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,8) }
            block.addView(TextView(this).apply {
                text = buildString {
                    appendLine(activity.className)
                    appendLine("Manifest=${if (activity.manifestEnabled) "ENABLED" else "DISABLED"}  Override=${activity.overrideState}  Effective=${if (activity.effectiveEnabled) "ENABLED" else "DISABLED"}")
                    append("exported=${activity.exported} permission=${activity.requiredPermission ?: "-"}")
                }
                setTextIsSelectable(true)
            })
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            controls.addView(button("Enable") { changeState(activity, ComponentOverrideState.ENABLED, false) })
            controls.addView(button("Disable") { changeState(activity, ComponentOverrideState.DISABLED, false) })
            controls.addView(button("Restore default") { changeState(activity, ComponentOverrideState.DEFAULT, false) })
            controls.addView(button("Enable & Launch") { changeState(activity, ComponentOverrideState.ENABLED, true) })
            block.addView(controls)
            block.addView(button("Launch") { launch(activity) })
            list.addView(block)
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun decision(activity: DiscoveredActivity, desired: ComponentOverrideState): ComponentControlDecision {
        val shizukuState = AndroidShizukuRuntime().probe()
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        return componentRouter.resolve(
            ComponentControlRequest(
                packageName = activity.packageName,
                className = activity.className,
                targetUserId = targetUserId(),
                sameUid = activity.sameUid && targetUserId() == currentUser,
                shizukuAvailable = shizukuState.binderAlive && shizukuState.permissionGranted,
                systemPrivilegedAvailable = false,
                criticalSystemComponent = isCriticalSystemComponent(activity),
                developerLab = DpcUiPreferences.read(this).developerMode,
                desiredState = desired
            )
        )
    }

    private fun isCriticalSystemComponent(activity: DiscoveredActivity): Boolean {
        if (activity.packageName == packageName) return false
        return activity.packageName in setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.systemui"
        )
    }

    private fun changeState(activity: DiscoveredActivity, desired: ComponentOverrideState, launchAfter: Boolean) {
        val decision = decision(activity, desired)
        if (!decision.allowed) {
            status.text = "${decision.status}\n${decision.detail}\nRisk=${decision.risk}\nPROTECTED_DPC_COMPONENT=${decision.status == ComponentControlStatus.PROTECTED_DPC_COMPONENT}"
            return
        }
        snapshotStore.save(
            ComponentStateSnapshot(targetUserId(), activity.packageName, activity.className, activity.overrideState, System.currentTimeMillis())
        )
        Thread {
            val detail = when (decision.route) {
                ComponentControlRoute.OWN_UID -> {
                    val r = stateGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, desired)
                    "route=OWN_UID requested=$desired observed=${r.observedState} Effective=${r.effectiveEnabled} ${r.detail}"
                }
                ComponentControlRoute.SHIZUKU -> {
                    val r = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId(), desired)
                    "route=SHIZUKU requested=$desired ${r.detail}; SUBMITTED, cross-user readback not claimed"
                }
                ComponentControlRoute.SYSTEM_PRIVILEGED -> "SYSTEM_COMPONENT_CONTROL route not active in this runtime"
                ComponentControlRoute.UNAVAILABLE -> decision.detail
            }
            runOnUiThread {
                status.text = detail
                if (launchAfter && decision.route == ComponentControlRoute.OWN_UID && detail.contains("VERIFIED")) launch(activity)
                else if (launchAfter && decision.route == ComponentControlRoute.SHIZUKU && detail.contains("SUBMITTED")) {
                    val exit = shizuku.startActivity(activity.packageName, activity.className, targetUserId())
                    status.append("\nlaunch exit=$exit")
                }
                scan()
            }
        }.start()
    }

    private fun batchPreviewFiltered(desired: ComponentOverrideState) {
        val filter = filterInput.text.toString().trim().lowercase()
        val selected = currentActivities.filter { filter.isBlank() || it.className.lowercase().contains(filter) }
        pendingBatchState = desired
        pendingBatch = selected.map { it to decision(it, desired) }
        val plan = ComponentBatchPlanner().plan(android.os.Build.VERSION.SDK_INT, pendingBatch.map { it.second })
        val atomicLabel = if (plan.atomic) "ATOMIC" else "BATCH_NOT_ATOMIC"
        status.text = buildString {
            appendLine("Batch Preview")
            appendLine("selected=${selected.size} desired=$desired atomic=${plan.atomic} $atomicLabel status=${plan.status}")
            pendingBatch.forEach { (activity, d) ->
                appendLine("${activity.className}: ${activity.overrideState} -> $desired route=${d.route} allowed=${d.allowed} risk=${d.risk} ${d.status}")
            }
        }
    }

    private fun applyBatch() {
        val desired = pendingBatchState ?: run { status.text = "Run Batch Preview first"; return }
        val allowed = pendingBatch.filter { it.second.allowed }
        if (allowed.isEmpty()) { status.text = "Apply batch\nNo supported component changes"; return }
        val plan = ComponentBatchPlanner().plan(android.os.Build.VERSION.SDK_INT, pendingBatch.map { it.second })
        allowed.forEach { (activity, _) ->
            snapshotStore.save(ComponentStateSnapshot(targetUserId(), activity.packageName, activity.className, activity.overrideState, System.currentTimeMillis()))
        }
        Thread {
            val results = mutableListOf<String>()
            if (plan.atomic && allowed.all { it.second.route == ComponentControlRoute.OWN_UID }) {
                val changes = allowed.map { (activity, _) ->
                    ComponentStateChange(ComponentName(activity.packageName, activity.className), desired)
                }
                val manifestStates = allowed.associate { (activity, _) ->
                    ComponentName(activity.packageName, activity.className) to activity.manifestEnabled
                }
                val applied = stateGateway.setStates(changes, manifestStates)
                applied.results.forEachIndexed { index, result ->
                    val activity = allowed[index].first
                    results += "${activity.className}: route=OWN_UID atomic=${applied.atomic} requested=$desired observed=${result.observedState} Effective=${result.effectiveEnabled} ${result.detail}"
                }
            } else {
                results += "BATCH_NOT_ATOMIC"
                allowed.forEach { (activity, d) ->
                    val line = when (d.route) {
                        ComponentControlRoute.OWN_UID -> {
                            val r = stateGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, desired)
                            "${activity.className}: route=OWN_UID requested=$desired observed=${r.observedState} Effective=${r.effectiveEnabled} ${r.detail}"
                        }
                        ComponentControlRoute.SHIZUKU -> {
                            val r = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId(), desired)
                            "${activity.className}: route=SHIZUKU requested=$desired ${r.detail}; readback via rescan"
                        }
                        ComponentControlRoute.SYSTEM_PRIVILEGED -> "${activity.className}: SYSTEM_COMPONENT_CONTROL route not active in this runtime"
                        ComponentControlRoute.UNAVAILABLE -> "${activity.className}: ${d.detail}"
                    }
                    results += line
                }
            }
            pendingBatch.filterNot { it.second.allowed }.forEach { (activity, d) ->
                results += "${activity.className}: SKIPPED ${d.status} ${d.detail}"
            }
            runOnUiThread {
                status.text = "Apply batch\n${results.joinToString("\n")}"
                scan()
            }
        }.start()
    }

    private fun restoreSnapshot() {
        val pkg = packageNameInput.text.toString().trim()
        val snapshots = snapshotStore.list(pkg, targetUserId())
        if (snapshots.isEmpty()) { status.text = "No snapshot for $pkg user ${targetUserId()}"; return }
        val byClass = currentActivities.associateBy { it.className }
        val results = mutableListOf<String>()
        snapshots.forEach { snap ->
            val activity = byClass[snap.className]
            if (activity == null) results += "${snap.className}: COMPONENT_NOT_FOUND"
            else {
                val d = decision(activity, snap.previousOverrideState)
                results += "${snap.className}: restore ${snap.previousOverrideState} route=${d.route} allowed=${d.allowed}"
                if (d.allowed) {
                    when (d.route) {
                        ComponentControlRoute.OWN_UID -> stateGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, snap.previousOverrideState)
                        ComponentControlRoute.SHIZUKU -> ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId(), snap.previousOverrideState)
                        else -> Unit
                    }
                }
            }
        }
        snapshotStore.clear(pkg, targetUserId())
        status.text = "Restore snapshot\n${results.joinToString("\n")}"
        scan()
    }

    private fun launch(activity: DiscoveredActivity) {
        val shizukuState = AndroidShizukuRuntime().probe()
        val input = ActivityAccessInput(
            packageName = activity.packageName,
            className = activity.className,
            enabled = activity.effectiveEnabled,
            exported = activity.exported,
            launcherVisible = activity.launcherVisible,
            sameUid = activity.sameUid,
            userAccessible = activity.userAccessible,
            shizukuAccessible = shizukuState.binderAlive && shizukuState.permissionGranted
        )
        val executor = ActivityExecutorRouter(
            AndroidActivityRouteExecutor(this, UserHandle.of(targetUserId())),
            mapOf(ActivityRoute.SHIZUKU to ShizukuActivityRouteExecutor(shizuku, targetUserId()))
        )
        val result = ActivityLaunchCoordinator(ActivityAccessPlanner(), executor).launch(input)
        status.text = "selected=${result.selectedRoute}\nblockers=${result.blockers}\nattempts=${result.attempts.joinToString { "${it.route}:${it.success}" }}"
    }
}
