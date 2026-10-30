package io.dpcaio.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.installer.android.AndroidInstallSourceReader
import io.dpcaio.shizuku.AndroidShizukuRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ActivityLauncherHubActivity : Activity() {
    private enum class AppTab { ALL, SYSTEM, USER, FAVORITES, RECENT }
    private enum class AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }
    private enum class SortDirection { ASCENDING, DESCENDING }
    private enum class StateFilter { ALL, ENABLED, DISABLED }
    private enum class InstallSourceFilter { ALL, INSTALLED, PRELOADED }
    private enum class TargetSdkFilter { ALL, CURRENT, LEGACY }
    private enum class DebuggableFilter { ALL, DEBUGGABLE, RELEASE }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val versionName: String,
        val uid: Int,
        val firstInstallTime: Long,
        val lastUpdateTime: Long,
        val targetSdk: Int,
        val installerPackage: String?,
        val system: Boolean,
        val enabled: Boolean,
        val debuggable: Boolean,
    )

    private lateinit var search: EditText
    private lateinit var tabBar: LinearLayout
    private lateinit var sortBar: LinearLayout
    private lateinit var stateBar: LinearLayout
    private lateinit var installSourceBar: LinearLayout
    private lateinit var targetSdkBar: LinearLayout
    private lateinit var debuggableBar: LinearLayout
    private lateinit var filterSummary: TextView
    private lateinit var resetFiltersButton: Button
    private lateinit var selectionBar: LinearLayout
    private lateinit var stats: LinearLayout
    private lateinit var appList: LinearLayout
    private lateinit var status: TextView

    private var currentTab = AppTab.ALL
    private var currentSort = AppSort.NAME
    private var currentSortDirection = SortDirection.ASCENDING
    private var currentStateFilter = StateFilter.ALL
    private var currentInstallSourceFilter = InstallSourceFilter.ALL
    private var currentTargetSdkFilter = TargetSdkFilter.ALL
    private var currentDebuggableFilter = DebuggableFilter.ALL
    private var installedApps: List<AppEntry> = emptyList()
    private val selectedPackages = linkedSetOf<String>()

    private val runtime = AndroidShizukuRuntime()
    private val installSourceReader by lazy { AndroidInstallSourceReader(this) }
    private val iconCache = LruCache<String, Drawable>(ICON_CACHE_SIZE)
    private val iconExecutor = Executors.newFixedThreadPool(ICON_WORKER_COUNT)
    private var renderGeneration = 0L
    private var inventoryGeneration = 0L
    private var visibleAppLimit = INITIAL_RENDER_LIMIT

    private val preferences: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val favoritePackages: Set<String>
        get() = preferences.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    private val recentPackages: List<String>
        get() = preferences.getString(KEY_RECENTS, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_RECENT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(STATE_SELECTED_PACKAGES)?.let { selectedPackages.addAll(it) }
        title = "Activity Launcher"
        renderShell()
        discoverInstalledApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_SELECTED_PACKAGES, ArrayList(selectedPackages))
        super.onSaveInstanceState(outState)
    }

    override fun onRestart() {
        super.onRestart()
        discoverInstalledApps(resetLimit = false)
    }

    override fun onResume() {
        super.onResume()
        if (::appList.isInitialized && installedApps.isNotEmpty()) {
            renderTabs()
            renderSortBar()
            renderStateBar()
            renderInstallSourceBar()
            renderTargetSdkBar()
            renderDebuggableBar()
            renderFilterSummary()
            renderSelectionBar()
            renderApps(resetLimit = false)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) iconCache.evictAll()
    }

    override fun onDestroy() {
        inventoryGeneration++
        renderGeneration++
        iconExecutor.shutdownNow()
        iconCache.evictAll()
        super.onDestroy()
    }

    private fun renderShell() {
        val shizuku = runtime.probe()
        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Activity Launcher",
                subtitle = "App-manager style inventory with package metadata, multi-select, fast filters and guarded shortcuts to activities, permissions, AppOps, updates and Android app info.",
                eyebrow = if (shizuku.binderAlive && shizuku.permissionGranted) {
                    "SHIZUKU ${shizuku.identity.uppercase()} • READY"
                } else {
                    "APP & ACTIVITY CATALOG"
                },
            )
        )

        body.addView(ModernUi.section(this, "Applications"))
        body.addView(
            ModernUi.compactCard(this).apply {
                search = ModernUi.field(this@ActivityLauncherHubActivity, "Search app name, package, UID or installer")
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderApps()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(search)

                tabBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(tabBar)
                renderTabs()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                sortBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(sortBar)
                renderSortBar()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                stateBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(stateBar)
                renderStateBar()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                installSourceBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(installSourceBar)
                renderInstallSourceBar()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                targetSdkBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(targetSdkBar)
                renderTargetSdkBar()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                debuggableBar = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.VERTICAL }
                addView(debuggableBar)
                renderDebuggableBar()

                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                filterSummary = ModernUi.body(this@ActivityLauncherHubActivity, filterSummaryText())
                addView(filterSummary)
                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 6))
                resetFiltersButton = ModernUi.action(
                    this@ActivityLauncherHubActivity,
                    "Reset filters (${activeFilterCount()})",
                ) { resetFilters() }
                addView(resetFiltersButton)
                renderFilterControls()
            }
        )

        body.addView(ModernUi.section(this, "Selection"))
        selectionBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(selectionBar)
        renderSelectionBar()

        body.addView(ModernUi.section(this, "Overview"))
        stats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(stats)

        body.addView(ModernUi.section(this, "App catalog"))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appList)

        body.addView(ModernUi.section(this, "Latest action"))
        status = ModernUi.outputCard(this, "Discovering installed applications…")
        body.addView(status)

        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun activeFilterCount(): Int {
        var count = 0
        if (currentStateFilter != StateFilter.ALL) count++
        if (currentInstallSourceFilter != InstallSourceFilter.ALL) count++
        if (currentTargetSdkFilter != TargetSdkFilter.ALL) count++
        if (currentDebuggableFilter != DebuggableFilter.ALL) count++
        if (::search.isInitialized && search.text.toString().isNotBlank()) count++
        return count
    }

    private fun filterSummaryText(): String =
        "ACTIVE FILTERS ${activeFilterCount()} • FILTERS: state=${currentStateFilter.name} • source=${currentInstallSourceFilter.name} • target=${currentTargetSdkFilter.name} • debug=${currentDebuggableFilter.name}"

    private fun renderFilterControls() {
        if (!::resetFiltersButton.isInitialized) return
        resetFiltersButton.text = "Reset filters (${activeFilterCount()})"
        resetFiltersButton.isEnabled = activeFilterCount() > 0
        resetFiltersButton.alpha = if (resetFiltersButton.isEnabled) 1f else 0.55f
    }

    private fun renderFilterSummary() {
        if (::filterSummary.isInitialized) filterSummary.text = filterSummaryText()
        renderFilterControls()
    }

    private fun resetFilters() {
        currentStateFilter = StateFilter.ALL
        currentInstallSourceFilter = InstallSourceFilter.ALL
        currentTargetSdkFilter = TargetSdkFilter.ALL
        currentDebuggableFilter = DebuggableFilter.ALL
        if (::search.isInitialized) search.setText("")
        renderStateBar()
        renderInstallSourceBar()
        renderTargetSdkBar()
        renderDebuggableBar()
        renderFilterSummary()
        renderApps()
    }

    private fun installedPackageNames(): Set<String> = installedApps.asSequence().map { it.packageName }.toSet()

    private fun tabCandidateCount(): Int {
        val installedPackages = installedPackageNames()
        return when (currentTab) {
            AppTab.ALL -> installedApps.size
            AppTab.SYSTEM -> installedApps.count { it.system }
            AppTab.USER -> installedApps.count { !it.system }
            AppTab.FAVORITES -> favoritePackages.count { it in installedPackages }
            AppTab.RECENT -> recentPackages.count { it in installedPackages }
        }
    }

    private fun tabLabel(tab: AppTab): String {
        val installedPackages = installedPackageNames()
        val count = when (tab) {
            AppTab.ALL -> installedApps.size
            AppTab.SYSTEM -> installedApps.count { it.system }
            AppTab.USER -> installedApps.count { !it.system }
            AppTab.FAVORITES -> favoritePackages.count { it in installedPackages }
            AppTab.RECENT -> recentPackages.count { it in installedPackages }
        }
        return "${tab.name} ($count)"
    }

    private fun renderTabs() {
        if (!::tabBar.isInitialized) return
        tabBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        AppTab.entries.forEach { tab ->
            val tone = when (tab) {
                AppTab.SYSTEM -> ModernUi.Tone.WARNING
                AppTab.USER -> ModernUi.Tone.SUCCESS
                AppTab.ALL, AppTab.FAVORITES, AppTab.RECENT -> ModernUi.Tone.NEUTRAL
            }
            val pill = ModernUi.pill(this, tabLabel(tab), tab == currentTab, tone) {
                currentTab = tab
                renderTabs()
                renderSortBar()
                renderApps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        tabBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            }
        )
    }

    private fun renderSortBar() {
        if (!::sortBar.isInitialized) return
        sortBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        AppSort.entries.forEach { sort ->
            val pill = ModernUi.pill(
                this,
                "SORT ${sort.name}",
                selected = sort == currentSort && currentTab != AppTab.RECENT,
                tone = ModernUi.Tone.NEUTRAL,
            ) {
                currentSort = sort
                currentSortDirection = when (sort) {
                    AppSort.NAME, AppSort.PACKAGE -> SortDirection.ASCENDING
                    AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> SortDirection.DESCENDING
                }
                renderSortBar()
                renderApps()
            }
            pill.isEnabled = currentTab != AppTab.RECENT
            pill.alpha = if (currentTab == AppTab.RECENT) 0.55f else 1f
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        sortBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
        if (currentTab == AppTab.RECENT) {
            sortBar.addView(
                ModernUi.body(this, "Recent keeps most-recent-first order.").apply {
                    setPadding(ModernUi.dp(this@ActivityLauncherHubActivity, 2), ModernUi.dp(this@ActivityLauncherHubActivity, 5), 0, 0)
                }
            )
        } else {
            sortBar.addView(ModernUi.spacer(this, 6))
            sortBar.addView(
                ModernUi.action(this, "Direction: ${sortDirectionLabel()}") { toggleSortDirection() }
            )
        }
    }

    private fun sortDirectionLabel(): String = when (currentSort) {
        AppSort.NAME, AppSort.PACKAGE -> if (currentSortDirection == SortDirection.ASCENDING) "A → Z" else "Z → A"
        AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> if (currentSortDirection == SortDirection.ASCENDING) "Oldest → newest" else "Newest → oldest"
    }

    private fun toggleSortDirection() {
        currentSortDirection = when (currentSortDirection) {
            SortDirection.ASCENDING -> SortDirection.DESCENDING
            SortDirection.DESCENDING -> SortDirection.ASCENDING
        }
        renderSortBar()
        renderApps()
    }

    private fun renderStateBar() {
        if (!::stateBar.isInitialized) return
        stateBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        StateFilter.entries.forEach { filter ->
            val tone = when (filter) {
                StateFilter.ALL -> ModernUi.Tone.NEUTRAL
                StateFilter.ENABLED -> ModernUi.Tone.SUCCESS
                StateFilter.DISABLED -> ModernUi.Tone.WARNING
            }
            val pill = ModernUi.pill(this, "STATE ${filter.name}", filter == currentStateFilter, tone) {
                currentStateFilter = filter
                renderStateBar()
                renderApps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        stateBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun renderInstallSourceBar() {
        if (!::installSourceBar.isInitialized) return
        installSourceBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        InstallSourceFilter.entries.forEach { filter ->
            val tone = when (filter) {
                InstallSourceFilter.ALL -> ModernUi.Tone.NEUTRAL
                InstallSourceFilter.INSTALLED -> ModernUi.Tone.SUCCESS
                InstallSourceFilter.PRELOADED -> ModernUi.Tone.WARNING
            }
            val pill = ModernUi.pill(this, "SOURCE ${filter.name}", filter == currentInstallSourceFilter, tone) {
                currentInstallSourceFilter = filter
                renderInstallSourceBar()
                renderApps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        installSourceBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun renderTargetSdkBar() {
        if (!::targetSdkBar.isInitialized) return
        targetSdkBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        TargetSdkFilter.entries.forEach { filter ->
            val tone = when (filter) {
                TargetSdkFilter.ALL -> ModernUi.Tone.NEUTRAL
                TargetSdkFilter.CURRENT -> ModernUi.Tone.SUCCESS
                TargetSdkFilter.LEGACY -> ModernUi.Tone.WARNING
            }
            val pill = ModernUi.pill(this, "TARGET SDK ${filter.name}", filter == currentTargetSdkFilter, tone) {
                currentTargetSdkFilter = filter
                renderTargetSdkBar()
                renderApps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        targetSdkBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun renderDebuggableBar() {
        if (!::debuggableBar.isInitialized) return
        debuggableBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DebuggableFilter.entries.forEach { filter ->
            val tone = when (filter) {
                DebuggableFilter.ALL -> ModernUi.Tone.NEUTRAL
                DebuggableFilter.DEBUGGABLE -> ModernUi.Tone.WARNING
                DebuggableFilter.RELEASE -> ModernUi.Tone.SUCCESS
            }
            val pill = ModernUi.pill(this, "DEBUG ${filter.name}", filter == currentDebuggableFilter, tone) {
                currentDebuggableFilter = filter
                renderDebuggableBar()
                renderApps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(pill)
        }
        debuggableBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun renderSelectionBar() {
        if (!::selectionBar.isInitialized) return
        selectionBar.removeAllViews()
        selectionBar.addView(
            ModernUi.compactCard(this).apply {
                addView(
                    ModernUi.body(
                        this@ActivityLauncherHubActivity,
                        "Selected: ${selectedPackages.size}. Batch actions here only update local launcher metadata or copy package names; package-state mutations remain in guarded manager surfaces.",
                    )
                )
                addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@ActivityLauncherHubActivity,
                        ModernUi.action(this@ActivityLauncherHubActivity, "Select visible") { selectVisible() },
                        ModernUi.action(this@ActivityLauncherHubActivity, "Clear selection") { clearSelection() },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@ActivityLauncherHubActivity,
                        ModernUi.action(this@ActivityLauncherHubActivity, "Copy selected packages") { copySelectedPackages() },
                        ModernUi.action(this@ActivityLauncherHubActivity, "Favorite selected") { favoriteSelected() },
                    )
                )
            }
        )
    }

    private fun discoverInstalledApps(resetLimit: Boolean = true) {
        val generation = ++inventoryGeneration
        if (::status.isInitialized) status.text = "Reading installed application inventory…"
        Thread {
            val applications = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
                }
            }.getOrElse { emptyList() }

            val mapped = applications.mapNotNull(::toEntry).distinctBy { it.packageName }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != inventoryGeneration) return@runOnUiThread
                iconCache.evictAll()
                installedApps = mapped
                selectedPackages.retainAll(installedPackageNames())
                status.text = "Installed app inventory loaded: ${mapped.size} packages"
                renderTabs()
                renderSortBar()
                renderStateBar()
                renderInstallSourceBar()
                renderTargetSdkBar()
                renderDebuggableBar()
                renderFilterSummary()
                renderSelectionBar()
                renderApps(resetLimit = resetLimit)
            }
        }.start()
    }

    private fun toEntry(info: ApplicationInfo): AppEntry? = runCatching {
        val label = info.loadLabel(packageManager).toString().ifBlank { info.packageName }
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(info.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(info.packageName, 0)
        }
        AppEntry(
            packageName = info.packageName,
            label = label,
            versionName = packageInfo.versionName ?: "unknown",
            uid = info.uid,
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            targetSdk = info.targetSdkVersion,
            installerPackage = installerPackage(info.packageName),
            system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            enabled = info.enabled,
            debuggable = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
    }.getOrNull()

    private fun installerPackage(packageName: String): String? =
        installSourceReader.read(packageName).installingPackageName

    private fun loadAppIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val loaded = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        iconCache.put(packageName, loaded)
        return loaded
    }

    private fun appIconView(app: AppEntry, icon: Drawable): ImageView = ImageView(this).apply {
        setImageDrawable(icon)
        contentDescription = "${app.label} icon"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(
            ModernUi.dp(this@ActivityLauncherHubActivity, 4),
            ModernUi.dp(this@ActivityLauncherHubActivity, 4),
            ModernUi.dp(this@ActivityLauncherHubActivity, 4),
            ModernUi.dp(this@ActivityLauncherHubActivity, 4),
        )
        layoutParams = FrameLayout.LayoutParams(
            ModernUi.dp(this@ActivityLauncherHubActivity, 54),
            ModernUi.dp(this@ActivityLauncherHubActivity, 54),
        )
    }

    private fun bindAppIcon(slot: FrameLayout, app: AppEntry, tone: ModernUi.Tone, generation: Long) {
        slot.tag = app.packageName
        slot.removeAllViews()
        iconCache.get(app.packageName)?.let {
            slot.addView(appIconView(app, it))
            return
        }
        slot.addView(ModernUi.monogramTile(this, app.label, tone))
        if (generation != renderGeneration || iconExecutor.isShutdown) return
        runCatching {
            iconExecutor.submit {
                if (generation != renderGeneration || Thread.currentThread().isInterrupted) return@submit
                val loaded = loadAppIcon(app.packageName) ?: return@submit
                if (generation != renderGeneration || Thread.currentThread().isInterrupted) return@submit
                runOnUiThread {
                    if (isFinishing || isDestroyed || generation != renderGeneration || slot.tag != app.packageName) return@runOnUiThread
                    slot.removeAllViews()
                    slot.addView(appIconView(app, loaded))
                }
            }
        }
    }

    private fun filteredApps(): List<AppEntry> {
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase() else ""
        val favorites = favoritePackages
        val recents = recentPackages
        val base = when (currentTab) {
            AppTab.ALL -> installedApps
            AppTab.SYSTEM -> installedApps.filter { it.system }
            AppTab.USER -> installedApps.filter { !it.system }
            AppTab.FAVORITES -> installedApps.filter { it.packageName in favorites }
            AppTab.RECENT -> recents.mapNotNull { pkg -> installedApps.find { it.packageName == pkg } }
        }
        val stateFiltered = when (currentStateFilter) {
            StateFilter.ALL -> base
            StateFilter.ENABLED -> base.filter { it.enabled }
            StateFilter.DISABLED -> base.filter { !it.enabled }
        }
        val sourceFiltered = when (currentInstallSourceFilter) {
            InstallSourceFilter.ALL -> stateFiltered
            InstallSourceFilter.INSTALLED -> stateFiltered.filter { !it.installerPackage.isNullOrBlank() }
            InstallSourceFilter.PRELOADED -> stateFiltered.filter { it.installerPackage.isNullOrBlank() }
        }
        val targetFiltered = when (currentTargetSdkFilter) {
            TargetSdkFilter.ALL -> sourceFiltered
            TargetSdkFilter.CURRENT -> sourceFiltered.filter { it.targetSdk >= Build.VERSION.SDK_INT }
            TargetSdkFilter.LEGACY -> sourceFiltered.filter { it.targetSdk < Build.VERSION.SDK_INT }
        }
        val debugFiltered = when (currentDebuggableFilter) {
            DebuggableFilter.ALL -> targetFiltered
            DebuggableFilter.DEBUGGABLE -> targetFiltered.filter { it.debuggable }
            DebuggableFilter.RELEASE -> targetFiltered.filter { !it.debuggable }
        }
        val filtered = debugFiltered.filter { app ->
            query.isBlank() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.uid.toString().contains(query) ||
                app.installerPackage?.lowercase()?.contains(query) == true
        }
        if (currentTab == AppTab.RECENT) return filtered
        val sorted = when (currentSort) {
            AppSort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            AppSort.PACKAGE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.packageName })
            AppSort.INSTALL_DATE -> filtered.sortedBy { it.firstInstallTime }
            AppSort.UPDATE_DATE -> filtered.sortedBy { it.lastUpdateTime }
        }
        return if (currentSortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted
    }

    private fun formatInstallDate(time: Long): String {
        if (time <= 0L) return "unknown"
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
    }

    private fun renderApps(resetLimit: Boolean = true) {
        if (!::appList.isInitialized || isFinishing || isDestroyed) return
        if (resetLimit) visibleAppLimit = INITIAL_RENDER_LIMIT
        val generation = ++renderGeneration
        val shown = filteredApps()
        if (::filterSummary.isInitialized) {
            filterSummary.text = "${filterSummaryText()} • SHOWING ${shown.size} / ${tabCandidateCount()}"
        }
        renderFilterControls()
        appList.removeAllViews()
        renderStats(shown)
        renderSelectionBar()

        if (shown.isEmpty()) {
            appList.addView(
                ModernUi.compactCard(this).apply {
                    addView(ModernUi.title(this@ActivityLauncherHubActivity, "No applications"))
                    addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 4))
                    addView(ModernUi.body(this@ActivityLauncherHubActivity, "No apps match the selected tab, state/source/target SDK/debug filters and search query."))
                }
            )
            return
        }

        shown.take(visibleAppLimit).forEach { app -> appList.addView(renderApp(app, generation)) }
        addLoadMoreControl(shown, generation)
    }

    private fun addLoadMoreControl(shown: List<AppEntry>, generation: Long) {
        if (visibleAppLimit >= shown.size) return
        val remaining = shown.size - visibleAppLimit
        val control = ModernUi.action(this, "Load more ($remaining remaining)") { appendNextPage(shown, generation) }
        control.tag = LOAD_MORE_TAG
        appList.addView(control)
    }

    private fun appendNextPage(shown: List<AppEntry>, generation: Long) {
        if (!::appList.isInitialized || isFinishing || isDestroyed || generation != renderGeneration) return
        val start = visibleAppLimit.coerceAtMost(shown.size)
        val nextLimit = (visibleAppLimit + RENDER_PAGE_SIZE).coerceAtMost(shown.size)
        val last = if (appList.childCount > 0) appList.getChildAt(appList.childCount - 1) else null
        if (last?.tag == LOAD_MORE_TAG) appList.removeView(last)
        shown.subList(start, nextLimit).forEach { app -> appList.addView(renderApp(app, generation)) }
        visibleAppLimit = nextLimit
        addLoadMoreControl(shown, generation)
    }

    private fun renderStats(shown: List<AppEntry>) {
        if (!::stats.isInitialized) return
        stats.removeAllViews()
        val installedPackages = installedPackageNames()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun add(value: String, label: String, tone: ModernUi.Tone) {
            val tile = ModernUi.metricTile(this, value, label, tone)
            tile.layoutParams = LinearLayout.LayoutParams(
                ModernUi.dp(this@ActivityLauncherHubActivity, 116),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 8) }
            row.addView(tile)
        }
        add(installedApps.size.toString(), "TOTAL", ModernUi.Tone.NEUTRAL)
        add(shown.size.toString(), "VISIBLE", ModernUi.Tone.SUCCESS)
        add(selectedPackages.size.toString(), "SELECTED", if (selectedPackages.isEmpty()) ModernUi.Tone.NEUTRAL else ModernUi.Tone.SUCCESS)
        add(installedApps.count { !it.enabled }.toString(), "DISABLED", ModernUi.Tone.WARNING)
        add(favoritePackages.count { it in installedPackages }.toString(), "FAVORITES", ModernUi.Tone.NEUTRAL)
        add(recentPackages.count { it in installedPackages }.toString(), "RECENT", ModernUi.Tone.NEUTRAL)
        stats.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun renderApp(app: AppEntry, generation: Long): LinearLayout {
        val favorite = app.packageName in favoritePackages
        val selected = app.packageName in selectedPackages
        val tone = when {
            !app.enabled -> ModernUi.Tone.WARNING
            app.system -> ModernUi.Tone.NEUTRAL
            else -> ModernUi.Tone.SUCCESS
        }
        return ModernUi.compactCard(this, tone).apply {
            val identity = LinearLayout(this@ActivityLauncherHubActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP

                val iconSlot = FrameLayout(this@ActivityLauncherHubActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ModernUi.dp(this@ActivityLauncherHubActivity, 54),
                        ModernUi.dp(this@ActivityLauncherHubActivity, 54),
                    ).apply { marginEnd = ModernUi.dp(this@ActivityLauncherHubActivity, 12) }
                }
                bindAppIcon(iconSlot, app, tone, generation)
                addView(iconSlot)

                addView(
                    LinearLayout(this@ActivityLauncherHubActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(ModernUi.title(this@ActivityLauncherHubActivity, app.label))
                        addView(ModernUi.body(this@ActivityLauncherHubActivity, app.packageName))
                        addView(ModernUi.body(this@ActivityLauncherHubActivity, "v${app.versionName} • SDK ${app.targetSdk} • Installed ${formatInstallDate(app.firstInstallTime)}"))
                        addView(ModernUi.body(this@ActivityLauncherHubActivity, "Updated ${formatInstallDate(app.lastUpdateTime)} • UID ${app.uid}"))
                        addView(ModernUi.body(this@ActivityLauncherHubActivity, "Installer: ${app.installerPackage ?: "unknown / preloaded"}"))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            addView(identity)

            val badges = LinearLayout(this@ActivityLauncherHubActivity).apply { orientation = LinearLayout.HORIZONTAL }
            badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, if (app.system) "SYSTEM" else "USER", tone))
            badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, if (app.enabled) "ENABLED" else "DISABLED", if (app.enabled) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING))
            badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, "TARGET ${app.targetSdk}", ModernUi.Tone.NEUTRAL))
            badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, if (app.debuggable) "DEBUGGABLE" else "RELEASE", if (app.debuggable) ModernUi.Tone.WARNING else ModernUi.Tone.SUCCESS))
            if (selected) badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, "SELECTED", ModernUi.Tone.SUCCESS))
            if (favorite) badges.addView(ModernUi.chip(this@ActivityLauncherHubActivity, "FAVORITE", ModernUi.Tone.NEUTRAL))
            addView(
                HorizontalScrollView(this@ActivityLauncherHubActivity).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(badges)
                }
            )

            addView(ModernUi.spacer(this@ActivityLauncherHubActivity, 10))
            addView(ModernUi.action(this@ActivityLauncherHubActivity, "Open activities", primary = true) { openActivities(app) })
            addView(
                ModernUi.actionRow(
                    this@ActivityLauncherHubActivity,
                    ModernUi.action(this@ActivityLauncherHubActivity, "Open app info") { openAppInfo(app) },
                    ModernUi.action(this@ActivityLauncherHubActivity, "Permissions") { openPermissions(app) },
                )
            )
            addView(
                ModernUi.actionRow(
                    this@ActivityLauncherHubActivity,
                    ModernUi.action(this@ActivityLauncherHubActivity, "Update app") { openUpdater(app) },
                    ModernUi.action(this@ActivityLauncherHubActivity, "AppOps") { openAppOps(app) },
                )
            )
            addView(
                ModernUi.actionRow(
                    this@ActivityLauncherHubActivity,
                    ModernUi.action(this@ActivityLauncherHubActivity, "Copy package") { copyPackage(app) },
                    ModernUi.action(this@ActivityLauncherHubActivity, if (selected) "Deselect" else "Select") { toggleSelected(app.packageName) },
                )
            )
            addView(
                ModernUi.actionRow(
                    this@ActivityLauncherHubActivity,
                    ModernUi.action(this@ActivityLauncherHubActivity, if (favorite) "Remove favorite" else "Add favorite") { toggleFavorite(app.packageName) },
                    ModernUi.action(this@ActivityLauncherHubActivity, "Open app") { openApp(app) },
                )
            )
        }
    }

    private fun selectVisible() {
        val visible = filteredApps().take(visibleAppLimit).map { it.packageName }
        selectedPackages.addAll(visible)
        status.text = "Selected ${visible.size} visible package(s); total selected=${selectedPackages.size}"
        renderApps(resetLimit = false)
    }

    private fun clearSelection() {
        val count = selectedPackages.size
        selectedPackages.clear()
        status.text = "Cleared selection ($count package(s))"
        renderApps(resetLimit = false)
    }

    private fun toggleSelected(packageName: String) {
        if (!selectedPackages.add(packageName)) selectedPackages.remove(packageName)
        status.text = "Selected packages: ${selectedPackages.size}"
        renderApps(resetLimit = false)
    }

    private fun copySelectedPackages() {
        if (selectedPackages.isEmpty()) {
            status.text = "No selected packages"
            return
        }
        val text = selectedPackages.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("selected packages", text))
        status.text = "Copied ${selectedPackages.size} selected package name(s)"
    }

    private fun favoriteSelected() {
        if (selectedPackages.isEmpty()) {
            status.text = "No selected packages"
            return
        }
        val next = favoritePackages.toMutableSet().apply { addAll(selectedPackages) }
        preferences.edit().putStringSet(KEY_FAVORITES, next).apply()
        status.text = "Added ${selectedPackages.size} selected package(s) to favorites"
        renderTabs()
        renderApps(resetLimit = false)
    }

    private fun openApp(app: AppEntry) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            recordRecent(app.packageName)
            status.text = "Opened ${app.packageName}"
            renderTabs()
        } else {
            status.text = "No launcher intent for ${app.packageName}"
        }
    }

    private fun openAppInfo(app: AppEntry) {
        recordRecent(app.packageName)
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${app.packageName}")))
        status.text = "Opened Android app info for ${app.packageName}"
        renderTabs()
    }

    private fun openPermissions(app: AppEntry) {
        recordRecent(app.packageName)
        startActivity(
            Intent(this, PermissionManagerActivity::class.java)
                .putExtra(PermissionManagerActivity.EXTRA_TARGET_PACKAGE, app.packageName)
        )
        status.text = "Opened Permission Manager for ${app.packageName}"
        renderTabs()
    }

    private fun openUpdater(app: AppEntry) {
        recordRecent(app.packageName)
        startActivity(
            Intent(this, AppPackageManagerActivity::class.java)
                .putExtra(AppPackageManagerActivity.EXTRA_MODE, AppPackageManagerActivity.MODE_UPDATER)
                .putExtra(AppPackageManagerActivity.EXTRA_TARGET_PACKAGE, app.packageName)
        )
        status.text = "Opened updater for ${app.packageName}"
        renderTabs()
    }

    private fun openAppOps(app: AppEntry) {
        recordRecent(app.packageName)
        startActivity(
            Intent(this, AppOpsVerifiedEditorActivity::class.java)
                .putExtra(AppOpsVerifiedEditorActivity.EXTRA_TARGET_PACKAGE, app.packageName)
        )
        status.text = "Opened AppOps editor for ${app.packageName}"
        renderTabs()
    }

    private fun copyPackage(app: AppEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("package", app.packageName))
        status.text = "Copied package: ${app.packageName}"
    }

    private fun toggleFavorite(packageName: String) {
        val next = favoritePackages.toMutableSet()
        if (!next.add(packageName)) next.remove(packageName)
        preferences.edit().putStringSet(KEY_FAVORITES, next).apply()
        renderTabs()
        renderApps(resetLimit = false)
        status.text = if (packageName in next) "Added to favorites: $packageName" else "Removed from favorites: $packageName"
    }

    private fun recordRecent(packageName: String) {
        val next = buildList {
            add(packageName)
            addAll(recentPackages.filterNot { it == packageName })
        }.take(MAX_RECENT)
        preferences.edit().putString(KEY_RECENTS, next.joinToString("\n")).apply()
    }

    private fun openActivities(app: AppEntry) {
        recordRecent(app.packageName)
        renderTabs()
        status.text = "Opening guarded activity inventory for ${app.packageName}"
        startActivity(Intent(this, ActivityExplorerActivity::class.java).putExtra("package", app.packageName))
    }

    companion object {
        private const val PREFS_NAME = "activity_launcher_hub"
        private const val KEY_FAVORITES = "favoritePackages"
        private const val KEY_RECENTS = "recentPackages"
        private const val STATE_SELECTED_PACKAGES = "selectedPackages"
        private const val MAX_RECENT = 24
        private const val ICON_CACHE_SIZE = 48
        private const val ICON_WORKER_COUNT = 2
        private const val INITIAL_RENDER_LIMIT = 40
        private const val RENDER_PAGE_SIZE = 40
        private const val LOAD_MORE_TAG = "activity_launcher_load_more"
    }
}
