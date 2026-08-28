package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
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
import io.dpcaio.activity.ActivityBrowserFilter
import io.dpcaio.activity.ActivityBrowserMatcher
import io.dpcaio.activity.ActivityExecutorRouter
import io.dpcaio.activity.ActivityLaunchCoordinator
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.AppScope
import io.dpcaio.activity.AppSortMode
import io.dpcaio.activity.ComponentBatchPlanner
import io.dpcaio.activity.ComponentControlDecision
import io.dpcaio.activity.ComponentControlRequest
import io.dpcaio.activity.ComponentControlRoute
import io.dpcaio.activity.ComponentControlRouter
import io.dpcaio.activity.ComponentControlStatus
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.DiscoveredActivity
import io.dpcaio.activity.EnabledStateFilter
import io.dpcaio.activity.ExportedStateFilter
import io.dpcaio.activity.InstalledAppDescriptor
import io.dpcaio.activity.LauncherStateFilter
import io.dpcaio.activity.PermissionStateFilter
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
import java.util.concurrent.ConcurrentHashMap

class ActivityExplorerActivity : Activity() {
    private lateinit var targetUserInput: EditText
    private lateinit var queryInput: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var scopeButton: Button
    private lateinit var enabledButton: Button
    private lateinit var exportedButton: Button
    private lateinit var launcherButton: Button
    private lateinit var permissionButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var groupFilterButton: Button
    private lateinit var sortButton: Button

    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val stateGateway by lazy { AndroidComponentStateGateway(this) }
    private val componentRouter = ComponentControlRouter()
    private val snapshotStore by lazy { ComponentStateSnapshotStore(this) }
    private val inventory by lazy { AndroidActivityInventory(this) }
    private val favoriteStore by lazy { ActivityFavoriteStore(this) }

    private var currentApps: List<InstalledAppDescriptor> = emptyList()
    private val loadedActivities = ConcurrentHashMap<String, List<DiscoveredActivity>>()
    private val expandedPackages = linkedSetOf<String>()
    private var filter = ActivityBrowserFilter()
    private var pendingBatch: List<Pair<DiscoveredActivity, ComponentControlDecision>> = emptyList()
    private var pendingBatchState: ComponentOverrideState? = null
    @Volatile private var scanGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Activity Manager 3.0"
        filter = filter.copy(favoritesOnly = intent.getBooleanExtra("favoritesOnly", false))
        buildUi()
        shizuku.bind()
        scanAllApps(forceReload = true)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(16, 16, 16, 16)
        }
        root.addView(TextView(this).apply {
            text = "Activity Manager 3.0\nAll installed applications • expand app → activities • favorites • groups • filters"
            setTextIsSelectable(true)
        })
        targetUserInput = EditText(this).apply {
            hint = "Target user ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(AndroidUserId.fromUid(Process.myUid()).toString())
        }
        queryInput = EditText(this).apply {
            hint = "Search app / package / activity / permission"
            setSingleLine(true)
        }
        root.addView(targetUserInput)
        root.addView(queryInput)

        scopeButton = filterButton("Apps", filter.appScope) { filter = filter.copy(appScope = next(filter.appScope)); refreshFilterButtons() }
        enabledButton = filterButton("State", filter.enabledState) { filter = filter.copy(enabledState = next(filter.enabledState)); refreshFilterButtons() }
        exportedButton = filterButton("Exported", filter.exportedState) { filter = filter.copy(exportedState = next(filter.exportedState)); refreshFilterButtons() }
        launcherButton = filterButton("Launcher", filter.launcherState) { filter = filter.copy(launcherState = next(filter.launcherState)); refreshFilterButtons() }
        permissionButton = filterButton("Permission", filter.permissionState) { filter = filter.copy(permissionState = next(filter.permissionState)); refreshFilterButtons() }
        favoritesButton = Button(this).apply {
            setOnClickListener {
                filter = filter.copy(favoritesOnly = !filter.favoritesOnly)
                refreshFilterButtons()
            }
        }
        groupFilterButton = Button(this).apply { setOnClickListener { cycleGroupFilter() } }
        sortButton = filterButton("Sort", filter.sortMode) { filter = filter.copy(sortMode = next(filter.sortMode)); refreshFilterButtons() }

        root.addView(horizontalScrollRow(scopeButton, enabledButton, exportedButton, launcherButton))
        root.addView(horizontalScrollRow(permissionButton, favoritesButton, groupFilterButton, sortButton))
        refreshFilterButtons()

        root.addView(horizontalScrollRow(
            Button(this).apply { text = "Search / Apply filters"; isAllCaps = false; setOnClickListener { applyFiltersDeep() } },
            Button(this).apply { text = "Rescan apps"; isAllCaps = false; setOnClickListener { scanAllApps(forceReload = true) } },
            Button(this).apply { text = "Manage favorite groups"; isAllCaps = false; setOnClickListener { showGroupManager() } },
            Button(this).apply { text = "Collapse all"; isAllCaps = false; setOnClickListener { expandedPackages.clear(); renderApps() } },
        ))

        root.addView(TextView(this).apply { text = "Batch actions apply only to currently loaded + filtered activities. Protected components remain blocked." })
        root.addView(horizontalScrollRow(
            Button(this).apply { text = "Preview Enable"; isAllCaps = false; setOnClickListener { batchPreviewFiltered(ComponentOverrideState.ENABLED) } },
            Button(this).apply { text = "Preview Disable"; isAllCaps = false; setOnClickListener { batchPreviewFiltered(ComponentOverrideState.DISABLED) } },
            Button(this).apply { text = "Preview Restore"; isAllCaps = false; setOnClickListener { batchPreviewFiltered(ComponentOverrideState.DEFAULT) } },
            Button(this).apply { text = "Apply batch"; isAllCaps = false; setOnClickListener { applyBatch() } },
        ))
        status = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(list)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        DpcUiShell.install(this, root)
        setContentView(root)
    }

    private fun targetUserId(): Int = targetUserInput.text.toString().trim().toIntOrNull()
        ?: AndroidUserId.fromUid(Process.myUid())

    private fun targetUserHandle(): UserHandle = UserHandle.getUserHandleForUid(AndroidUserId.baseUid(targetUserId()))

    private fun scanAllApps(forceReload: Boolean) {
        val generation = ++scanGeneration
        if (forceReload) {
            loadedActivities.clear()
            expandedPackages.clear()
        }
        status.text = "Scanning installed applications for user ${targetUserId()}..."
        Thread {
            val apps = runCatching { inventory.listApps(targetUserHandle()) }.getOrElse { emptyList() }
            if (generation != scanGeneration) return@Thread
            currentApps = apps
            runOnUiThread { renderApps() }
        }.start()
    }

    private fun applyFiltersDeep() {
        filter = filter.copy(query = queryInput.text.toString().trim())
        refreshFilterButtons()
        val generation = ++scanGeneration
        val needsActivityIndex = filter.query.isNotBlank() ||
            filter.enabledState != EnabledStateFilter.ALL ||
            filter.exportedState != ExportedStateFilter.ALL ||
            filter.launcherState != LauncherStateFilter.ALL ||
            filter.permissionState != PermissionStateFilter.ALL ||
            filter.favoritesOnly || filter.favoriteGroup != null
        if (!needsActivityIndex) {
            renderApps()
            return
        }
        status.text = "Indexing activities for filters... apps=${currentApps.size}"
        Thread {
            var loaded = 0
            currentApps.forEach { app ->
                if (generation != scanGeneration) return@Thread
                if (!loadedActivities.containsKey(app.packageName)) {
                    loadedActivities[app.packageName] = safeLoadActivities(app.packageName)
                }
                loaded++
                if (loaded % 25 == 0) runOnUiThread { status.text = "Indexing activities... $loaded/${currentApps.size}" }
            }
            if (generation == scanGeneration) runOnUiThread { renderApps() }
        }.start()
    }

    private fun safeLoadActivities(packageName: String): List<DiscoveredActivity> {
        if (packageName == KNOXZT_PACKAGE) {
            runCatching { KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = shizuku).ensureReady() }
        }
        return runCatching { inventory.list(packageName, targetUserHandle()) }.getOrElse { emptyList() }
    }

    private fun toggleExpanded(app: InstalledAppDescriptor) {
        if (!expandedPackages.add(app.packageName)) {
            expandedPackages.remove(app.packageName)
            renderApps()
            return
        }
        if (loadedActivities.containsKey(app.packageName)) {
            renderApps()
            return
        }
        status.text = "Loading ${app.packageName} activities..."
        Thread {
            loadedActivities[app.packageName] = safeLoadActivities(app.packageName)
            runOnUiThread { renderApps() }
        }.start()
    }

    private fun renderApps() {
        list.removeAllViews()
        filter = filter.copy(query = queryInput.text.toString().trim())
        val shown = ActivityBrowserMatcher.sortApps(currentApps.filter(::appVisible), filter.sortMode)
        status.text = "Activity Manager 3.0\napps=${currentApps.size} shown=${shown.size} loadedActivities=${loadedActivities.values.sumOf { it.size }} user=${targetUserId()}"
        shown.forEach { app -> addAppBlock(app) }
        if (shown.isEmpty()) list.addView(TextView(this).apply { text = "No applications match current filters." })
    }

    private fun appVisible(app: InstalledAppDescriptor): Boolean {
        val appKey = favoriteStore.appItemKey(app.packageName)
        val appFavorite = favoriteStore.isAppFavorite(app.packageName)
        val appGroups = favoriteStore.groupsFor(appKey)
        val scopeOnly = filter.copy(
            query = "",
            enabledState = EnabledStateFilter.ALL,
            exportedState = ExportedStateFilter.ALL,
            launcherState = LauncherStateFilter.ALL,
            permissionState = PermissionStateFilter.ALL,
            favoritesOnly = false,
            favoriteGroup = null,
        )
        if (!ActivityBrowserMatcher.matchesApp(app, scopeOnly, true, emptySet())) return false
        if (ActivityBrowserMatcher.matchesApp(app, filter, appFavorite, appGroups)) return true
        return loadedActivities[app.packageName].orEmpty().any { activity -> activityVisible(activity) }
    }

    private fun activityVisible(activity: DiscoveredActivity): Boolean {
        val key = favoriteStore.activityItemKey(activity.packageName, activity.className)
        return ActivityBrowserMatcher.matchesActivity(
            activity,
            filter,
            favoriteStore.isActivityFavorite(activity.packageName, activity.className),
            favoriteStore.groupsFor(key),
        )
    }

    private fun addAppBlock(app: InstalledAppDescriptor) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(0, 10, 0, 14)
        }
        val favorite = favoriteStore.isAppFavorite(app.packageName)
        val expanded = app.packageName in expandedPackages
        val titleButton = Button(this).apply {
            text = "${if (expanded) "▼" else "▶"} ${if (favorite) "★" else "☆"} ${app.label}\n${app.packageName} • activities=${app.activityCount}${if (app.systemApp) " • SYSTEM" else " • USER"}"
            isAllCaps = false
            textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
            setPaddingDp(12, 10, 12, 10)
            setOnClickListener { toggleExpanded(app) }
        }
        block.addView(titleButton)
        block.addView(twoButtonRow(
            Button(this).apply {
                text = if (favorite) "Unfavorite app" else "Favorite app"
                setOnClickListener { favoriteStore.toggleAppFavorite(app.packageName); renderApps() }
            },
            Button(this).apply {
                text = "App groups"
                setOnClickListener { showAssignGroups(favoriteStore.appItemKey(app.packageName), app.label) }
            },
        ))
        block.addView(Button(this).apply {
            text = "Restore snapshot for this app"
            setOnClickListener { restoreSnapshot(app.packageName) }
        })

        if (expanded) {
            val activities = loadedActivities[app.packageName].orEmpty().filter(::activityVisible)
            if (activities.isEmpty()) {
                block.addView(TextView(this).apply { text = "No activities match filters (or package has none)." })
            } else {
                activities.forEach { addActivityBlock(block, it) }
            }
        }
        list.addView(block)
    }

    private fun addActivityBlock(parent: LinearLayout, activity: DiscoveredActivity) {
        val favorite = favoriteStore.isActivityFavorite(activity.packageName, activity.className)
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(16, 8, 0, 12)
        }
        block.addView(TextView(this).apply {
            text = buildString {
                append(if (favorite) "★ " else "☆ ")
                appendLine(activity.className)
                appendLine("Manifest=${if (activity.manifestEnabled) "ENABLED" else "DISABLED"}  Override=${activity.overrideState}  Effective=${if (activity.effectiveEnabled) "ENABLED" else "DISABLED"}")
                append("exported=${activity.exported} launcher=${activity.launcherVisible} permission=${activity.requiredPermission ?: "-"}")
            }
            setTextIsSelectable(true)
        })
        block.addView(twoButtonRow(
            Button(this).apply { text = if (favorite) "★ Unfavorite" else "☆ Favorite"; setOnClickListener { favoriteStore.toggleActivityFavorite(activity.packageName, activity.className); renderApps() } },
            Button(this).apply { text = "Groups"; setOnClickListener { showAssignGroups(favoriteStore.activityItemKey(activity.packageName, activity.className), activity.className) } },
        ))
        block.addView(twoButtonRow(
            Button(this).apply { text = "Enable"; setOnClickListener { changeState(activity, ComponentOverrideState.ENABLED, false) } },
            Button(this).apply { text = "Disable"; setOnClickListener { changeState(activity, ComponentOverrideState.DISABLED, false) } },
        ))
        block.addView(twoButtonRow(
            Button(this).apply { text = "Restore default"; setOnClickListener { changeState(activity, ComponentOverrideState.DEFAULT, false) } },
            Button(this).apply { text = "Enable & Launch"; setOnClickListener { changeState(activity, ComponentOverrideState.ENABLED, true) } },
        ))
        block.addView(Button(this).apply { text = "Launch"; setOnClickListener { launch(activity) } })
        parent.addView(block)
    }

    private fun filteredLoadedActivities(): List<DiscoveredActivity> = loadedActivities
        .filterKeys { it in expandedPackages }
        .values
        .flatten()
        .filter(::activityVisible)

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
        val decision = decision(activity, desired)
        if (!decision.allowed) {
            status.text = "${decision.status}\n${decision.detail}\nRisk=${decision.risk}\nPROTECTED_DPC_COMPONENT=${decision.status == ComponentControlStatus.PROTECTED_DPC_COMPONENT}"
            return
        }
        snapshotStore.save(ComponentStateSnapshot(targetUserId(), activity.packageName, activity.className, activity.overrideState, System.currentTimeMillis()))
        Thread {
            val detail = when (decision.route) {
                ComponentControlRoute.OWN_UID -> {
                    val r = stateGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, desired)
                    "route=OWN_UID requested=$desired observed=${r.observedState} Effective=${r.effectiveEnabled} ${r.detail}"
                }
                ComponentControlRoute.SHIZUKU -> {
                    val r = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId(), desired)
                    "route=SHIZUKU requested=$desired ${r.detail}; readback via rescan"
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
                reloadPackage(activity.packageName)
            }
        }.start()
    }

    private fun reloadPackage(packageName: String) {
        Thread {
            loadedActivities[packageName] = safeLoadActivities(packageName)
            runOnUiThread { renderApps() }
        }.start()
    }

    private fun batchPreviewFiltered(desired: ComponentOverrideState) {
        val selected = filteredLoadedActivities()
        pendingBatchState = desired
        pendingBatch = selected.map { it to decision(it, desired) }
        val plan = ComponentBatchPlanner().plan(android.os.Build.VERSION.SDK_INT, pendingBatch.map { it.second })
        status.text = buildString {
            appendLine("Batch Preview")
            appendLine("selected=${selected.size} desired=$desired atomic=${plan.atomic} status=${plan.status}")
            pendingBatch.take(100).forEach { (activity, d) ->
                appendLine("${activity.packageName}/${activity.className}: ${activity.overrideState} -> $desired route=${d.route} allowed=${d.allowed} risk=${d.risk} ${d.status}")
            }
            if (pendingBatch.size > 100) appendLine("... ${pendingBatch.size - 100} more")
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
                val changes = allowed.map { (activity, _) -> ComponentStateChange(ComponentName(activity.packageName, activity.className), desired) }
                val manifestStates = allowed.associate { (activity, _) -> ComponentName(activity.packageName, activity.className) to activity.manifestEnabled }
                val applied = stateGateway.setStates(changes, manifestStates)
                applied.results.forEachIndexed { index, result ->
                    val activity = allowed[index].first
                    results += "${activity.className}: route=OWN_UID atomic=${applied.atomic} requested=$desired observed=${result.observedState} Effective=${result.effectiveEnabled} ${result.detail}"
                }
            } else {
                results += "BATCH_NOT_ATOMIC"
                allowed.forEach { (activity, d) ->
                    results += when (d.route) {
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
                }
            }
            pendingBatch.filterNot { it.second.allowed }.forEach { (activity, d) -> results += "${activity.className}: SKIPPED ${d.status} ${d.detail}" }
            val changedPackages = allowed.map { it.first.packageName }.toSet()
            changedPackages.forEach { loadedActivities[it] = safeLoadActivities(it) }
            runOnUiThread { status.text = "Apply batch\n${results.take(120).joinToString("\n")}"; renderApps() }
        }.start()
    }

    private fun restoreSnapshot(packageName: String) {
        val snapshots = snapshotStore.list(packageName, targetUserId())
        if (snapshots.isEmpty()) { status.text = "No snapshot for $packageName user ${targetUserId()}"; return }
        val results = mutableListOf<String>()
        Thread {
            val activities = loadedActivities[packageName] ?: safeLoadActivities(packageName)
            val byClass = activities.associateBy { it.className }
            snapshots.forEach { snap ->
                val activity = byClass[snap.className]
                if (activity == null) results += "${snap.className}: COMPONENT_NOT_FOUND"
                else {
                    val d = decision(activity, snap.previousOverrideState)
                    results += "${snap.className}: restore ${snap.previousOverrideState} route=${d.route} allowed=${d.allowed}"
                    if (d.allowed) when (d.route) {
                        ComponentControlRoute.OWN_UID -> stateGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, snap.previousOverrideState)
                        ComponentControlRoute.SHIZUKU -> ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId(), snap.previousOverrideState)
                        else -> Unit
                    }
                }
            }
            snapshotStore.clear(packageName, targetUserId())
            loadedActivities[packageName] = safeLoadActivities(packageName)
            runOnUiThread { status.text = "Restore snapshot\n${results.joinToString("\n")}"; renderApps() }
        }.start()
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
            shizukuAccessible = shizukuState.binderAlive && shizukuState.permissionGranted,
        )
        val executor = ActivityExecutorRouter(
            AndroidActivityRouteExecutor(this, targetUserHandle()),
            mapOf(ActivityRoute.SHIZUKU to ShizukuActivityRouteExecutor(shizuku, targetUserId())),
        )
        val result = ActivityLaunchCoordinator(ActivityAccessPlanner(), executor).launch(input)
        status.text = "selected=${result.selectedRoute}\nblockers=${result.blockers}\nattempts=${result.attempts.joinToString { "${it.route}:${it.success}" }}"
    }

    private fun showGroupManager() {
        val groups = favoriteStore.groups().toList()
        AlertDialog.Builder(this)
            .setTitle("Favorite groups")
            .setMessage(if (groups.isEmpty()) "No groups yet." else groups.joinToString("\n"))
            .setPositiveButton("Create") { _, _ -> promptCreateGroup() }
            .setNeutralButton("Rename / Delete") { _, _ -> chooseGroupForManagement() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptCreateGroup() {
        val input = EditText(this).apply { hint = "Group name"; setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("Create favorite group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val created = favoriteStore.createGroup(input.text.toString())
                status.text = if (created) "Group created" else "Group not created (empty or duplicate)"
                refreshFilterButtons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseGroupForManagement() {
        val groups = favoriteStore.groups().toList()
        if (groups.isEmpty()) { status.text = "No favorite groups"; return }
        AlertDialog.Builder(this)
            .setTitle("Choose group")
            .setItems(groups.toTypedArray()) { _, which -> showGroupActions(groups[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupActions(group: String) {
        AlertDialog.Builder(this)
            .setTitle(group)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) promptRenameGroup(group) else confirmDeleteGroup(group)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRenameGroup(group: String) {
        val input = EditText(this).apply { setText(group); setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("Rename group")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val renamed = favoriteStore.renameGroup(group, input.text.toString())
                if (filter.favoriteGroup == group && renamed) filter = filter.copy(favoriteGroup = input.text.toString().trim().replace(Regex("\\s+"), " "))
                status.text = if (renamed) "Group renamed" else "Rename failed"
                refreshFilterButtons(); renderApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteGroup(group: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete group?")
            .setMessage(group)
            .setPositiveButton("Delete") { _, _ ->
                favoriteStore.deleteGroup(group)
                if (filter.favoriteGroup == group) filter = filter.copy(favoriteGroup = null)
                refreshFilterButtons(); renderApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAssignGroups(itemKey: String, label: String) {
        val groups = favoriteStore.groups().toList()
        if (groups.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No favorite groups")
                .setMessage("Create a group first?")
                .setPositiveButton("Create") { _, _ -> promptCreateGroup() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val checked = BooleanArray(groups.size) { itemKey in favoriteStore.members(groups[it]) }
        AlertDialog.Builder(this)
            .setTitle("Groups: $label")
            .setMultiChoiceItems(groups.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Save") { _, _ ->
                groups.forEachIndexed { index, group -> favoriteStore.setMembership(group, itemKey, checked[index]) }
                renderApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cycleGroupFilter() {
        val values = listOf<String?>(null) + favoriteStore.groups().toList()
        val index = values.indexOf(filter.favoriteGroup).let { if (it < 0) 0 else it }
        filter = filter.copy(favoriteGroup = values[(index + 1) % values.size])
        refreshFilterButtons()
    }

    private fun refreshFilterButtons() {
        scopeButton.text = "Apps: ${filter.appScope}"
        enabledButton.text = "State: ${filter.enabledState}"
        exportedButton.text = "Exported: ${filter.exportedState}"
        launcherButton.text = "Launcher: ${filter.launcherState}"
        permissionButton.text = "Permission: ${filter.permissionState}"
        favoritesButton.text = "Favorites: ${if (filter.favoritesOnly) "ONLY" else "ALL"}"
        groupFilterButton.text = "Group: ${filter.favoriteGroup ?: "ALL"}"
        sortButton.text = "Sort: ${filter.sortMode}"
    }

    private inline fun <reified T : Enum<T>> next(value: T): T {
        val values = enumValues<T>()
        return values[(value.ordinal + 1) % values.size]
    }

    private fun <T> filterButton(prefix: String, initial: T, action: () -> Unit): Button = Button(this).apply {
        text = "$prefix: $initial"
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun twoButtonRow(first: Button, second: Button): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}
