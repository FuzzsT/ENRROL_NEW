package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.shizuku.AndroidShizukuRuntime
import io.dpcaio.shizuku.ShizukuUserServiceClient

class ControlCenterActivity : Activity() {
    private enum class ControlTab { FUNCTIONS, MODULES, SHIZUKU }

    private data class FunctionEntry(
        val title: String,
        val subtitle: String,
        val target: Class<out Activity>,
        val tone: ModernUi.Tone = ModernUi.Tone.NEUTRAL,
    )

    private lateinit var tabs: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var currentTab = ControlTab.FUNCTIONS
    private val shizukuRuntime = AndroidShizukuRuntime()
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private var shizukuObserver: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Control Center"
        currentTab = runCatching {
            ControlTab.valueOf(intent.getStringExtra("tab") ?: ControlTab.FUNCTIONS.name)
        }.getOrDefault(ControlTab.FUNCTIONS)
        shizuku.bind()
        render()
    }

    override fun onStart() {
        super.onStart()
        if (shizukuObserver == null) {
            shizukuObserver = shizukuRuntime.observe { state ->
                if (state.binderAlive && state.permissionGranted && !shizuku.isConnected()) {
                    shizuku.bind()
                }
                if (::content.isInitialized && currentTab == ControlTab.SHIZUKU) {
                    renderContent()
                }
                if (::status.isInitialized) {
                    status.text = buildString {
                        append("Shizuku: ")
                        append(if (state.binderAlive) "binder online" else "binder offline")
                        append(" • backend=")
                        append(state.backend)
                        append(" • service=")
                        append(if (shizuku.isConnected()) "connected" else "disconnected")
                        append(" • permission=")
                        append(if (state.permissionGranted) "granted" else "missing")
                        append(" • identity=")
                        append(state.identity)
                    }
                }
            }
        }
    }

    override fun onStop() {
        shizukuObserver?.close()
        shizukuObserver = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) renderContent()
    }

    private fun render() {
        val body = ModernUi.page(this)
        val shizukuState = shizukuRuntime.probe()
        body.addView(
            ModernUi.hero(
                this,
                title = "Control Center",
                subtitle = "Choose a function, inspect integrated modules, or manage the official Shizuku execution route from one surface.",
                eyebrow = when {
                    shizukuState.binderAlive && shizukuState.permissionGranted -> {
                        "${shizukuState.backend.uppercase()} ${shizukuState.identity.uppercase()} • READY"
                    }
                    shizukuState.binderAlive -> {
                        "${shizukuState.backend.uppercase()} SERVER • AUTH NEEDED"
                    }
                    else -> "ENTERPRISE MODULE & FUNCTION HUB"
                },
            )
        )

        tabs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(tabs)
        renderTabs()

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(content)

        body.addView(ModernUi.section(this, "Runtime status"))
        status = ModernUi.outputCard(this, "Ready")
        body.addView(status)

        setContentView(ModernUi.scrollPage(this, body))
        renderContent()
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ControlTab.entries.forEach { tab ->
            val tone = when (tab) {
                ControlTab.FUNCTIONS -> ModernUi.Tone.NEUTRAL
                ControlTab.MODULES -> ModernUi.Tone.SUCCESS
                ControlTab.SHIZUKU -> ModernUi.Tone.WARNING
            }
            val pill = ModernUi.pill(this, tab.name, tab == currentTab, tone) {
                currentTab = tab
                renderTabs()
                renderContent()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ControlCenterActivity, 8) }
            row.addView(pill)
        }
        tabs.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            }
        )
    }

    private fun renderContent() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        when (currentTab) {
            ControlTab.FUNCTIONS -> renderFunctions()
            ControlTab.MODULES -> renderModules()
            ControlTab.SHIZUKU -> renderShizuku()
        }
    }

    private fun renderFunctions() {
        content.addView(ModernUi.section(this, "Functions"))
        val functions = listOf(
            FunctionEntry("Activity Launcher", "Browse installed apps, favorites, recent packages and guarded activities.", ActivityLauncherHubActivity::class.java, ModernUi.Tone.SUCCESS),
            FunctionEntry("Self Update", "Verified DPC-AIO updates with silent install when Android policy allows it.", SelfUpdateActivity::class.java, ModernUi.Tone.SUCCESS),
            FunctionEntry("Enterprise Policy Hub", "Device-owner policy capabilities and explicit apply/readback flows.", EnterprisePolicyHubActivity::class.java),
            FunctionEntry("Permission Manager", "Runtime permissions, AppOps and effective capability inspection.", PermissionManagerActivity::class.java),
            FunctionEntry("Verified AppOps", "Verified AppOps read, mutation, readback and rollback through the typed Shizuku route.", AppOpsVerifiedEditorActivity::class.java, ModernUi.Tone.WARNING),
            FunctionEntry("Module Center", "Full compile-time module registry and capability visibility.", ModuleCenterActivity::class.java),
            FunctionEntry("Network / DNS / DoH", "Network policy and private DNS controls.", NetworkControlActivity::class.java),
            FunctionEntry("Samsung Settings", "Supported Samsung settings routes with readback.", SamsungSettingsEditorActivity::class.java),
            FunctionEntry("Knox Enterprise", "Official Knox enterprise surfaces and capability gates.", KnoxEnterpriseCenterActivity::class.java),
            FunctionEntry("Diagnostics", "Runtime, ownership and integration diagnostics.", DpcDiagnosticsActivity::class.java),
        )
        functions.forEach { entry ->
            content.addView(
                ModernUi.compactCard(this, entry.tone).apply {
                    addView(ModernUi.title(this@ControlCenterActivity, entry.title))
                    addView(ModernUi.spacer(this@ControlCenterActivity, 4))
                    addView(ModernUi.body(this@ControlCenterActivity, entry.subtitle))
                    addView(ModernUi.spacer(this@ControlCenterActivity, 9))
                    addView(ModernUi.action(this@ControlCenterActivity, "Open", primary = true) {
                        startActivity(Intent(this@ControlCenterActivity, entry.target))
                    })
                }
            )
        }
    }

    private fun renderModules() {
        val management = ManagementContextFactory.create(this)
        val resolved = DpcModuleRegistry.modules.map { descriptor ->
            descriptor to CapabilityResolver.resolve(descriptor.requirements, management)
        }
        content.addView(ModernUi.section(this, "Integrated modules (${resolved.size})"))
        DpcModuleGroup.entries.forEach { group ->
            val items = resolved.filter { it.first.group == group }
            if (items.isEmpty()) return@forEach
            content.addView(ModernUi.section(this, "${group.label} (${items.size})"))
            items.forEach { (module, resolution) ->
                val tone = if (resolution.executable) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING
                content.addView(
                    ModernUi.compactCard(this, tone).apply {
                        addView(ModernUi.title(this@ControlCenterActivity, module.title))
                        addView(ModernUi.body(this@ControlCenterActivity, "${module.id} • ${module.surface}"))
                        addView(ModernUi.chip(this@ControlCenterActivity, resolution.availability.name, tone))
                        module.entryActivity?.let { target ->
                            addView(ModernUi.spacer(this@ControlCenterActivity, 8))
                            addView(ModernUi.action(this@ControlCenterActivity, "Open module") {
                                if (resolution.executable) startActivity(Intent(this@ControlCenterActivity, target))
                            }.apply { isEnabled = resolution.executable })
                        }
                    }
                )
            }
        }
    }

    private fun renderShizuku() {
        val state = shizukuRuntime.probe()
        val serviceConnected = shizuku.isConnected()
        val serviceIdentity = shizuku.identity()
        content.addView(ModernUi.section(this, "Shizuku Control"))

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun metric(value: String, label: String, tone: ModernUi.Tone) {
            val tile = ModernUi.metricTile(this, value, label, tone)
            tile.layoutParams = LinearLayout.LayoutParams(
                ModernUi.dp(this@ControlCenterActivity, 126),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@ControlCenterActivity, 8) }
            metrics.addView(tile)
        }
        metric(if (state.binderAlive) "ON" else "OFF", "BINDER", if (state.binderAlive) ModernUi.Tone.SUCCESS else ModernUi.Tone.DANGER)
        metric(state.backend.uppercase(), "BACKEND", if (state.binderAlive) ModernUi.Tone.SUCCESS else ModernUi.Tone.NEUTRAL)
        metric(if (serviceConnected) "ON" else "OFF", "SERVICE", if (serviceConnected) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING)
        metric(if (state.permissionGranted) "YES" else "NO", "PERMISSION", if (state.permissionGranted) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING)
        metric(state.identity.uppercase(), "IDENTITY", ModernUi.Tone.NEUTRAL)
        metric(state.apiVersion?.toString() ?: "—", "API", ModernUi.Tone.NEUTRAL)
        content.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(metrics)
            }
        )

        content.addView(ModernUi.section(this, "UserService"))
        content.addView(
            ModernUi.compactCard(this, if (serviceConnected) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING).apply {
                addView(ModernUi.title(this@ControlCenterActivity, serviceIdentity ?: "Not bound yet"))
                addView(ModernUi.body(this@ControlCenterActivity, "Backend=${state.backend} • Server UID=${state.serverUid ?: "unknown"} • route=${state.identity}"))
                addView(ModernUi.spacer(this@ControlCenterActivity, 9))
                addView(
                    ModernUi.actionRow(
                        this@ControlCenterActivity,
                        ModernUi.action(this@ControlCenterActivity, "Bind / reconnect", primary = true) {
                            val bound = shizuku.bind()
                            status.text = "Shizuku UserService bind submitted=$bound"
                            renderContent()
                        },
                        ModernUi.action(this@ControlCenterActivity, "Request permission") {
                            val requested = shizukuRuntime.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                            status.text = "Shizuku permission request submitted=$requested"
                        },
                    )
                )
                addView(ModernUi.action(this@ControlCenterActivity, "Refresh status") {
                    renderContent()
                    status.text = "Shizuku status refreshed"
                })
            }
        )

        content.addView(ModernUi.section(this, "Official capabilities"))
        content.addView(
            ModernUi.compactCard(this).apply {
                listOf(
                    "Activity launch",
                    "Component enable / disable",
                    "Runtime permission grant / revoke",
                    "AppOps read / set",
                    "Settings read / write / delete",
                    "Package enable / install-existing",
                    "Permission & sysconfig read-only inspection",
                ).forEach { capability ->
                    addView(ModernUi.chip(this@ControlCenterActivity, capability, ModernUi.Tone.NEUTRAL))
                }
                addView(ModernUi.spacer(this@ControlCenterActivity, 8))
                addView(ModernUi.body(this@ControlCenterActivity, "No arbitrary shell bridge is exposed. Operations stay typed through the existing Shizuku UserService contract."))
            }
        )
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 8401
    }
}
