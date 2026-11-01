package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
import io.dpcaio.activity.ComponentMutationVerification
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
    private enum class ActivityQuickFilter {
        ALL,
        ENABLED,
        DISABLED,
        EXPORTED,
        PRIVATE,
    }

    private lateinit var packageNameInput: EditText
    private lateinit var targetUserInput: EditText
    private lateinit var filterInput: EditText
    private lateinit var filterBar: LinearLayout
    private lateinit var statsStrip: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val stateGateway by lazy { AndroidComponentStateGateway(this) }
    private val componentRouter = ComponentControlRouter()
    private val snapshotStore by lazy { ComponentStateSnapshotStore(this) }
    private var currentActivities: List<DiscoveredActivity> = emptyList()
    private var currentQuickFilter = ActivityQuickFilter.ALL
    private var pendingBatch: List<Pair<DiscoveredActivity, ComponentControlDecision>> = emptyList()
    private var pendingBatchState: ComponentOverrideState? = null
    private var pendingBatchTargetUserId: Int? = null
    private var scanGeneration = 0L
    private var mutationGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Activity Launcher / Explorer"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Activity Launcher",
                subtitle = "Browse components like an app catalog, then launch through the safest available route. Privileged controls stay explicit and guarded.",
                eyebrow = "ACTIVITY EXPLORER",
            )
        )

        body.addView(ModernUi.section(this, "Package & user"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                packageNameInput = ModernUi.field(this@ActivityExplorerActivity, "Package name").apply {
                    setText(intent.getStringExtra("package") ?: KNOXZT_PACKAGE)
                }
                targetUserInput = ModernUi.field(
                    this@ActivityExplorerActivity,
                    "Target user ID",
                    InputType.TYPE_CLASS_NUMBER,
                ).apply {
                    val current = AndroidUserId.fromUid(Process.myUid())
                    val requested = intent.getIntExtra("userId", current).takeIf { it >= 0 } ?: current
                    setText(requested.toString())
                }
                addView(packageNameInput)
                addView(targetUserInput)
                addView(ModernUi.spacer(this@ActivityExplorerActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@ActivityExplorerActivity,
                        ModernUi.action(this@ActivityExplorerActivity, "Choose package") {
                            RuntimeTargetPicker.showPackagePicker(
                                this@ActivityExplorerActivity,
                                shizuku,
                                targetUserId(),
                                packageNameInput.text.toString().trim(),
                            ) { selected ->
                                packageNameInput.setText(selected)
                                scan()
                            }
                        },
                        ModernUi.action(this@ActivityExplorerActivity, "Choose user") {
                            val current = AndroidUserId.fromUid(Process.myUid())
                            RuntimeTargetPicker.showUserPicker(
                                this@ActivityExplorerActivity,
                                shizuku,
                                current,
                                targetUserId(),
                            ) { selected ->
                                targetUserInput.setText(selected.toString())
                                scan()
                            }
                        },
                    )
                )
                addView(
                    ModernUi.action(this@ActivityExplorerActivity, "Scan activities", primary = true) { scan() }
                )
            }
        )

        body.addView(ModernUi.section(this, "Find activities"))
        body.addView(
            ModernUi.compactCard(this).apply {
                filterInput = ModernUi.field(
                    this@ActivityExplorerActivity,
                    "Search class, package or permission",
                )
                filterInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        renderActivities(currentActivities)
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(filterInput)
                filterBar = LinearLayout(this@ActivityExplorerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(filterBar)
                renderFilterBar()
            }
        )

        body.addView(ModernUi.section(this, "Overview"))
        statsStrip = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(statsStrip)
        renderStatsStrip(emptyList(), emptyList())

        body.addView(ModernUi.section(this, "Activities"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        body.addView(ModernUi.section(this, "Latest action"))
        status = ModernUi.outputCard(this, "Ready. Scan a package to inspect its activities.")
        body.addView(status)

        body.addView(ModernUi.section(this, "Batch Preview"))
        body.addView(
            ModernUi.compactCard(this).apply {
                addView(
                    ModernUi.body(
                        this@ActivityExplorerActivity,
                        "Batch actions follow the visible filtered set. Unsupported or protected routes stay skipped.",
                    )
                )
                addView(ModernUi.spacer(this@ActivityExplorerActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@ActivityExplorerActivity,
                        ModernUi.action(this@ActivityExplorerActivity, "Batch Enable") {
                            batchPreviewFiltered(ComponentOverrideState.ENABLED)
                        },
                        ModernUi.action(this@ActivityExplorerActivity, "Batch Disable", destructive = true) {
                            batchPreviewFiltered(ComponentOverrideState.DISABLED)
                        },
                    )
                )
                addView(
                    ModernUi.action(this@ActivityExplorerActivity, "Batch Restore Default") {
                        batchPreviewFiltered(ComponentOverrideState.DEFAULT)
                    }
                )
                addView(ModernUi.spacer(this@ActivityExplorerActivity, 7))
                addView(
                    ModernUi.actionRow(
                        this@ActivityExplorerActivity,
                        ModernUi.action(this@ActivityExplorerActivity, "Apply batch", primary = true) { applyBatch() },
                        ModernUi.action(this@ActivityExplorerActivity, "Restore snapshot") { restoreSnapshot() },
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        shizuku.bind()
    }

    private fun targetUserId(): Int = targetUserInput.text.toString().trim().toIntOrNull()?.takeIf { it >= 0 }
        ?: AndroidUserId.fromUid(Process.myUid())

    private fun scan() {
        val pkg = packageNameInput.text.toString().trim()
        if (pkg.isBlank()) return
        val generation = ++scanGeneration
        val targetUserId = targetUserId()
        status.text = "Scanning $pkg for user $targetUserId..."
        Thread {
            if (pkg == KNOXZT_PACKAGE) {
                KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = shizuku).ensureReady()
            }
            val activities = runCatching {
                AndroidActivityInventory(this).list(
                    pkg,
                    UserHandle.getUserHandleForUid(AndroidUserId.representativeUid(targetUserId)),
                )
            }.getOrElse { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != scanGeneration) return@runOnUiThread
                currentActivities = activities
                renderActivities(activities)
            }
        }.start()
    }

    private fun renderFilterBar() {
        if (!::filterBar.isInitialized) return
        filterBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ActivityQuickFilter.entries.forEach { filter ->
            val pill = ModernUi.pill(
                this,
                filter.name,
                selected = filter == currentQuickFilter,
                tone = when (filter) {
                    ActivityQuickFilter.ENABLED -> ModernUi.Tone.SUCCESS
                    ActivityQuickFilter.DISABLED -> ModernUi.Tone.WARNING
                    ActivityQuickFilter.EXPORTED -> ModernUi.Tone.NEUTRAL
                    ActivityQuickFilter.PRIVATE -> ModernUi.Tone.WARNING
                    ActivityQuickFilter.ALL -> ModernUi.Tone.NEUTRAL
                },
            ) {
                currentQuickFilter = filter
                renderFilterBar()
                renderActivities(currentActivities)
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityExplorerActivity, 8) }
            row.addView(pill)
        }
        filterBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun applyActivityFilter(activities: List<DiscoveredActivity>): List<DiscoveredActivity> {
        val query = filterInput.text.toString().trim().lowercase()
        return activities.filter { activity ->
            val textMatch = query.isBlank() ||
                activity.className.lowercase().contains(query) ||
                activity.packageName.lowercase().contains(query) ||
                activity.requiredPermission?.lowercase()?.contains(query) == true
            val quickMatch = when (currentQuickFilter) {
                ActivityQuickFilter.ALL -> true
                ActivityQuickFilter.ENABLED -> activity.effectiveEnabled
                ActivityQuickFilter.DISABLED -> !activity.effectiveEnabled
                ActivityQuickFilter.EXPORTED -> activity.exported
                ActivityQuickFilter.PRIVATE -> !activity.exported
            }
            textMatch && quickMatch
        }
    }

    private fun renderStatsStrip(activities: List<DiscoveredActivity>, shown: List<DiscoveredActivity>) {
        if (!::statsStrip.isInitialized) return
        statsStrip.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addMetric(value: Int, label: String, tone: ModernUi.Tone) {
            val tile = ModernUi.metricTile(this, value.toString(), label, tone)
            tile.layoutParams = LinearLayout.LayoutParams(
                ModernUi.dp(this@ActivityExplorerActivity, 116),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityExplorerActivity, 8) }
            row.addView(tile)
        }
        addMetric(activities.size, "TOTAL", ModernUi.Tone.NEUTRAL)
        addMetric(shown.size, "VISIBLE", ModernUi.Tone.SUCCESS)
        addMetric(activities.count { it.exported }, "EXPORTED", ModernUi.Tone.NEUTRAL)
        addMetric(activities.count { !it.effectiveEnabled }, "DISABLED", ModernUi.Tone.WARNING)
        statsStrip.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun renderActivities(activities: List<DiscoveredActivity>) {
        list.removeAllViews()
        val shown = applyActivityFilter(activities)
        renderStatsStrip(activities, shown)
        status.text = buildString {
            appendLine("Activity Launcher")
            appendLine("TOTAL=${activities.size} FILTERED=${shown.size} FILTER=${currentQuickFilter.name}")
            append("Target user ID=${targetUserId()}")
        }

        if (shown.isEmpty()) {
            list.addView(
                ModernUi.compactCard(this).apply {
                    addView(ModernUi.title(this@ActivityExplorerActivity, "Nothing here"))
                    addView(ModernUi.spacer(this@ActivityExplorerActivity, 4))
                    addView(ModernUi.body(this@ActivityExplorerActivity, "No activities match the current search and quick filter."))
                }
            )
            return
        }

        shown.forEach { activity -> list.addView(renderActivityRow(activity)) }
    }

    private fun renderActivityIdentity(activity: DiscoveredActivity): LinearLayout {
        val tone = if (activity.effectiveEnabled) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(ModernUi.monogramTile(this@ActivityExplorerActivity, shortActivityName(activity), tone))
            addView(
                LinearLayout(this@ActivityExplorerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ModernUi.title(this@ActivityExplorerActivity, shortActivityName(activity)))
                    addView(
                        ModernUi.body(this@ActivityExplorerActivity, activity.className).apply {
                            setPadding(0, ModernUi.dp(this@ActivityExplorerActivity, 2), 0, 0)
                        }
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun renderActivityRow(activity: DiscoveredActivity): LinearLayout {
        val effectiveTone = if (activity.effectiveEnabled) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING
        val exposureTone = if (activity.exported) ModernUi.Tone.NEUTRAL else ModernUi.Tone.WARNING
        return ModernUi.compactCard(this, effectiveTone).apply {
            addView(renderActivityIdentity(activity))

            val badges = LinearLayout(this@ActivityExplorerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            fun addBadge(label: String, tone: ModernUi.Tone) {
                val badge = ModernUi.chip(this@ActivityExplorerActivity, label, tone)
                badge.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = ModernUi.dp(this@ActivityExplorerActivity, 7)
                    topMargin = ModernUi.dp(this@ActivityExplorerActivity, 8)
                }
                badges.addView(badge)
            }
            addBadge(if (activity.effectiveEnabled) "ENABLED" else "DISABLED", effectiveTone)
            addBadge(if (activity.exported) "EXPORTED" else "PRIVATE", exposureTone)
            if (activity.requiredPermission != null) addBadge("PERMISSION", ModernUi.Tone.NEUTRAL)
            addView(
                HorizontalScrollView(this@ActivityExplorerActivity).apply {
                    isHorizontalScrollBarEnabled = false
                    clipToPadding = false
                    addView(badges)
                }
            )

            addView(
                ModernUi.body(
                    this@ActivityExplorerActivity,
                    "Manifest ${if (activity.manifestEnabled) "enabled" else "disabled"}  •  Override ${activity.overrideState}  •  ${activity.requiredPermission ?: "No required permission"}",
                ).apply { setPadding(0, ModernUi.dp(this@ActivityExplorerActivity, 9), 0, 0) }
            )
            addView(ModernUi.spacer(this@ActivityExplorerActivity, 11))
            addView(
                ModernUi.action(this@ActivityExplorerActivity, "Launch", primary = true) { launch(activity) }
            )
            addView(ModernUi.divider(this@ActivityExplorerActivity))
            addView(
                ModernUi.body(this@ActivityExplorerActivity, "Advanced controls").apply {
                    setPadding(ModernUi.dp(this@ActivityExplorerActivity, 2), 0, 0, ModernUi.dp(this@ActivityExplorerActivity, 5))
                }
            )
            addView(
                ModernUi.actionRow(
                    this@ActivityExplorerActivity,
                    ModernUi.action(this@ActivityExplorerActivity, "Enable") {
                        changeState(activity, ComponentOverrideState.ENABLED, false)
                    },
                    ModernUi.action(this@ActivityExplorerActivity, "Disable", destructive = true) {
                        changeState(activity, ComponentOverrideState.DISABLED, false)
                    },
                )
            )
            addView(
                ModernUi.actionRow(
                    this@ActivityExplorerActivity,
                    ModernUi.action(this@ActivityExplorerActivity, "Restore default") {
                        changeState(activity, ComponentOverrideState.DEFAULT, false)
                    },
                    ModernUi.action(this@ActivityExplorerActivity, "Enable & Launch") {
                        changeState(activity, ComponentOverrideState.ENABLED, true)
                    },
                )
            )
        }
    }

    private fun shortActivityName(activity: DiscoveredActivity): String {
        val className = activity.className
        val relative = when {
            className.startsWith(activity.packageName + ".") -> className.removePrefix(activity.packageName + ".")
            className.startsWith(".") -> className.removePrefix(".")
            else -> className.substringAfterLast('.')
        }
        return relative.ifBlank { className }
    }

    private fun decision(
        activity: DiscoveredActivity,
        desired: ComponentOverrideState,
        targetUserId: Int = targetUserId(),
    ): ComponentControlDecision {
        val shizukuState = AndroidShizukuRuntime().probe()
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        return componentRouter.resolve(
            ComponentControlRequest(
                packageName = activity.packageName,
                className = activity.className,
                targetUserId = targetUserId,
                sameUid = activity.sameUid && targetUserId == currentUser,
                shizukuAvailable = shizukuState.binderAlive && shizukuState.permissionGranted,
                systemPrivilegedAvailable = false,
                criticalSystemComponent = isCriticalSystemComponent(activity),
                developerLab = DpcUiPreferences.read(this).developerMode,
                desiredState = desired,
            )
        )
    }

    private fun isCriticalSystemComponent(activity: DiscoveredActivity): Boolean {
        if (activity.packageName == packageName) return false
        return activity.packageName in setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.systemui",
        )
    }

    private fun changeState(activity: DiscoveredActivity, desired: ComponentOverrideState, launchAfter: Boolean) {
        val operationUserId = targetUserId()
        val decision = decision(activity, desired, operationUserId)
        if (!decision.allowed) {
            status.text = "${decision.status}\n${decision.detail}\nRisk=${decision.risk}\nPROTECTED_DPC_COMPONENT=${decision.status == ComponentControlStatus.PROTECTED_DPC_COMPONENT}"
            return
        }
        val generation = ++mutationGeneration
        snapshotStore.save(
            ComponentStateSnapshot(
                operationUserId,
                activity.packageName,
                activity.className,
                activity.overrideState,
                System.currentTimeMillis(),
            )
        )
        Thread {
            var shizukuVerifiedForLaunch = false
            val detail = when (decision.route) {
                ComponentControlRoute.OWN_UID -> {
                    val result = stateGateway.setState(
                        ComponentName(activity.packageName, activity.className),
                        activity.manifestEnabled,
                        desired,
                    )
                    "route=OWN_UID requested=$desired observed=${result.observedState} Effective=${result.effectiveEnabled} ${result.detail}"
                }
                ComponentControlRoute.SHIZUKU -> {
                    val result = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(
                        activity.packageName,
                        activity.className,
                        operationUserId,
                        desired,
                    )
                    shizukuVerifiedForLaunch = ComponentMutationVerification.canLaunchAfterEnable(
                        requested = desired,
                        observed = result.observedState,
                        verified = result.verified,
                    )
                    ComponentMutationVerification.statusLine(
                        route = "SHIZUKU",
                        userId = operationUserId,
                        requested = desired,
                        observed = result.observedState,
                        verified = result.verified,
                        detail = result.detail,
                    )
                }
                ComponentControlRoute.SYSTEM_PRIVILEGED -> "SYSTEM_COMPONENT_CONTROL route not active in this runtime"
                ComponentControlRoute.UNAVAILABLE -> decision.detail
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != mutationGeneration) return@runOnUiThread
                if (operationUserId != targetUserId()) return@runOnUiThread
                status.text = detail
                if (launchAfter && desired == ComponentOverrideState.ENABLED &&
                    ((decision.route == ComponentControlRoute.OWN_UID && detail.contains("VERIFIED")) ||
                        (decision.route == ComponentControlRoute.SHIZUKU && shizukuVerifiedForLaunch))
                ) {
                    launch(activity.copy(enabled = true, effectiveEnabled = true), operationUserId)
                } else {
                    scan()
                }
            }
        }.start()
    }

    private fun batchPreviewFiltered(desired: ComponentOverrideState) {
        val selected = applyActivityFilter(currentActivities)
        val previewUserId = targetUserId()
        pendingBatchState = desired
        pendingBatchTargetUserId = previewUserId
        pendingBatch = selected.map { decisionTarget ->
            decisionTarget to decision(decisionTarget, desired, previewUserId)
        }
        val plan = ComponentBatchPlanner().plan(android.os.Build.VERSION.SDK_INT, pendingBatch.map { it.second })
        val atomicLabel = if (plan.atomic) "ATOMIC" else "BATCH_NOT_ATOMIC"
        status.text = buildString {
            appendLine("Batch Preview")
            appendLine("selected=${selected.size} user=$previewUserId desired=$desired atomic=${plan.atomic} $atomicLabel status=${plan.status}")
            pendingBatch.forEach { (activity, decision) ->
                appendLine("${activity.className}: ${activity.overrideState} -> $desired route=${decision.route} allowed=${decision.allowed} risk=${decision.risk} ${decision.status}")
            }
        }
    }

    private fun applyBatch() {
        val desired = pendingBatchState ?: run {
            status.text = "Run Batch Preview first"
            return
        }
        val batch = pendingBatch.toList()
        val operationUserId = pendingBatchTargetUserId ?: run {
            status.text = "Run Batch Preview first"
            return
        }
        if (operationUserId != targetUserId()) {
            status.text = "Batch target changed. Run Batch Preview again"
            return
        }
        val allowed = batch.filter { it.second.allowed }
        if (allowed.isEmpty()) {
            status.text = "Apply batch\nNo supported component changes"
            return
        }
        val generation = ++mutationGeneration
        val plan = ComponentBatchPlanner().plan(android.os.Build.VERSION.SDK_INT, batch.map { it.second })
        allowed.forEach { (activity, _) ->
            snapshotStore.save(
                ComponentStateSnapshot(
                    operationUserId,
                    activity.packageName,
                    activity.className,
                    activity.overrideState,
                    System.currentTimeMillis(),
                )
            )
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
                allowed.forEach { (activity, decision) ->
                    val line = when (decision.route) {
                        ComponentControlRoute.OWN_UID -> {
                            val result = stateGateway.setState(
                                ComponentName(activity.packageName, activity.className),
                                activity.manifestEnabled,
                                desired,
                            )
                            "${activity.className}: route=OWN_UID requested=$desired observed=${result.observedState} Effective=${result.effectiveEnabled} ${result.detail}"
                        }
                        ComponentControlRoute.SHIZUKU -> {
                            val result = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(
                                activity.packageName,
                                activity.className,
                                operationUserId,
                                desired,
                            )
                            "${activity.className}: ${ComponentMutationVerification.statusLine(
                                route = "SHIZUKU",
                                userId = operationUserId,
                                requested = desired,
                                observed = result.observedState,
                                verified = result.verified,
                                detail = result.detail,
                            )}"
                        }
                        ComponentControlRoute.SYSTEM_PRIVILEGED -> "${activity.className}: SYSTEM_COMPONENT_CONTROL route not active in this runtime"
                        ComponentControlRoute.UNAVAILABLE -> "${activity.className}: ${decision.detail}"
                    }
                    results += line
                }
            }
            batch.filterNot { it.second.allowed }.forEach { (activity, decision) ->
                results += "${activity.className}: SKIPPED ${decision.status} ${decision.detail}"
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != mutationGeneration) return@runOnUiThread
                if (operationUserId != targetUserId()) return@runOnUiThread
                status.text = "Apply batch\n${results.joinToString("\n")}"
                scan()
            }
        }.start()
    }

    private fun restoreSnapshot() {
        val pkg = packageNameInput.text.toString().trim()
        val operationUserId = targetUserId()
        val snapshots = snapshotStore.list(pkg, operationUserId)
        if (snapshots.isEmpty()) {
            status.text = "No snapshot for $pkg user $operationUserId"
            return
        }
        val byClass = currentActivities.associateBy { it.className }
        val results = mutableListOf<String>()
        snapshots.forEach { snapshot ->
            val activity = byClass[snapshot.className]
            if (activity == null) {
                results += "${snapshot.className}: COMPONENT_NOT_FOUND"
            } else {
                val decision = decision(activity, snapshot.previousOverrideState, operationUserId)
                results += "${snapshot.className}: restore ${snapshot.previousOverrideState} route=${decision.route} allowed=${decision.allowed}"
                if (decision.allowed) {
                    when (decision.route) {
                        ComponentControlRoute.OWN_UID -> stateGateway.setState(
                            ComponentName(activity.packageName, activity.className),
                            activity.manifestEnabled,
                            snapshot.previousOverrideState,
                        )
                        ComponentControlRoute.SHIZUKU -> {
                            val result = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(
                                activity.packageName,
                                activity.className,
                                operationUserId,
                                snapshot.previousOverrideState,
                            )
                            results += "${snapshot.className}: ${ComponentMutationVerification.statusLine(
                                route = "SHIZUKU",
                                userId = operationUserId,
                                requested = snapshot.previousOverrideState,
                                observed = result.observedState,
                                verified = result.verified,
                                detail = result.detail,
                            )}"
                        }
                        else -> Unit
                    }
                }
            }
        }
        snapshotStore.clear(pkg, operationUserId)
        status.text = "Restore snapshot\n${results.joinToString("\n")}"
        scan()
    }

    private fun launch(activity: DiscoveredActivity, targetUserId: Int = targetUserId()) {
        status.text = "Launching ${activity.className} for user $targetUserId..."
        Thread {
            val shizukuState = AndroidShizukuRuntime().probe()
            val input = ActivityAccessInput(
                packageName = activity.packageName,
                className = activity.className,
                enabled = activity.effectiveEnabled,
                exported = activity.exported,
                launcherVisible = activity.launcherVisible,
                sameUid = activity.sameUid,
                userAccessible = activity.userAccessible,
                shizukuAccessible = shizukuState.binderAlive && shizukuState.permissionGranted,
            )
            val executor = ActivityExecutorRouter(
                AndroidActivityRouteExecutor(
                    this,
                    UserHandle.getUserHandleForUid(AndroidUserId.representativeUid(targetUserId)),
                ),
                mapOf(ActivityRoute.SHIZUKU to ShizukuActivityRouteExecutor(shizuku, targetUserId)),
            )
            val result = ActivityLaunchCoordinator(ActivityAccessPlanner(), executor).launch(input)
            val report = buildString {
                appendLine("selected=${result.selectedRoute}")
                appendLine("blockers=${result.blockers}")
                appendLine("attempts:")
                result.attempts.forEach { attempt ->
                    appendLine("- ${attempt.route}: success=${attempt.success} ${attempt.detail.orEmpty()}")
                }
            }.trimEnd()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = report
                if (result.selectedRoute != null) scan()
            }
        }.start()
    }
}