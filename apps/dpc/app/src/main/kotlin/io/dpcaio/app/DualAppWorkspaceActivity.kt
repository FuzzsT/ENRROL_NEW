package io.dpcaio.app

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.shizuku.AndroidShizukuRuntime
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.util.Locale

class DualAppWorkspaceActivity : Activity() {
    private data class Workspace(val id: Int, val name: String, val raw: String) {
        override fun toString(): String = "$name • user $id"
    }

    private data class AppChoice(val label: String, val packageName: String) {
        override fun toString(): String = "$label • $packageName"
    }

    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var workspaceSpinner: Spinner
    private lateinit var appSpinner: Spinner
    private lateinit var profileName: EditText

    private val runtime = AndroidShizukuRuntime()
    private lateinit var shizuku: ShizukuUserServiceClient
    private var workspaces: List<Workspace> = emptyList()
    private var apps: List<AppChoice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Dual Apps"
        shizuku = ShizukuUserServiceClient(this)

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Dual Apps",
                subtitle = "Run additional isolated instances using Android profiles. Repeated copies are separate profile instances, not repackaged APKs.",
                eyebrow = "PROFILE WORKSPACES",
            )
        )

        body.addView(ModernUi.section(this, "Shizuku capability"))
        body.addView(
            ModernUi.card(this).apply {
                diagnostics = ModernUi.body(this@DualAppWorkspaceActivity, "Checking Shizuku…")
                addView(diagnostics)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Request Shizuku permission") {
                    val requested = runtime.requestPermission(REQ_SHIZUKU)
                    status.text = if (requested) "Shizuku permission request opened" else "Shizuku service is unavailable"
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.body(this@DualAppWorkspaceActivity, "UID 1000 diagnostics are read-only. This module reports system identity when it is already present; it does not obtain or spoof UID 1000."))
            }
        )

        body.addView(ModernUi.section(this, "Create workspace"))
        body.addView(
            ModernUi.card(this).apply {
                profileName = ModernUi.field(this@DualAppWorkspaceActivity, "Profile name")
                profileName.setText("DualApp")
                addView(profileName)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Create Managed profile") {
                    createWorkspace(clone = false)
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Create Clone profile") {
                    createWorkspace(clone = true)
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.body(this@DualAppWorkspaceActivity, "Android/OEM profile limits still apply. Creating another workspace is the supported way to obtain another isolated app instance without changing the APK package name or signature."))
            }
        )

        body.addView(ModernUi.section(this, "Workspaces"))
        body.addView(
            ModernUi.card(this).apply {
                workspaceSpinner = Spinner(this@DualAppWorkspaceActivity)
                addView(workspaceSpinner)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Refresh workspaces") { refreshWorkspaces() })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Start selected workspace") {
                    selectedWorkspace()?.let { ws -> runWorkspaceAction("start", ws) { shizuku.startProfile(ws.id) } }
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Stop selected workspace") {
                    selectedWorkspace()?.let { ws -> runWorkspaceAction("stop", ws) { shizuku.stopProfile(ws.id) } }
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Remove selected workspace") {
                    selectedWorkspace()?.let { ws -> runWorkspaceAction("remove", ws) { shizuku.removeProfile(ws.id) } }
                })
            }
        )

        body.addView(ModernUi.section(this, "Clone application into workspace"))
        body.addView(
            ModernUi.card(this).apply {
                appSpinner = Spinner(this@DualAppWorkspaceActivity)
                addView(appSpinner)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Refresh applications") { refreshApps() })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Add app to selected workspace", primary = true) {
                    installSelectedApp()
                })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.body(this@DualAppWorkspaceActivity, "The operation uses the existing package already installed on the device. Each profile gets its own app data sandbox."))
            }
        )

        body.addView(ModernUi.section(this, "Status"))
        status = ModernUi.outputCard(this, "Ready")
        body.addView(status)

        setContentView(ModernUi.scrollPage(this, body))
        refreshCapability()
        refreshApps()
        bindAndRefresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            refreshCapability()
            bindAndRefresh()
        }
    }

    override fun onDestroy() {
        runCatching { shizuku.unbind() }
        super.onDestroy()
    }

    private fun refreshCapability() {
        val state = runtime.probe()
        diagnostics.text = buildString {
            appendLine("binder=${state.binderAlive}")
            appendLine("permission=${state.permissionGranted}")
            appendLine("serverUid=${state.serverUid ?: "unknown"}")
            appendLine("identity=${state.identity}")
            appendLine("api=${state.apiVersion ?: "unknown"}")
            append("UID 1000 diagnostics: ${if (state.serverUid == 1000) "SYSTEM UID PRESENT" else "not present"}")
        }
    }

    private fun bindAndRefresh() {
        val state = runtime.probe()
        if (!state.binderAlive || !state.permissionGranted) {
            status.text = "Shizuku is not ready. Start Shizuku and grant permission."
            return
        }
        val requested = shizuku.bind()
        status.text = if (requested) "Binding Shizuku workspace service…" else "Failed to bind Shizuku workspace service"
        workspaceSpinner.postDelayed({ refreshWorkspaces() }, 350)
    }

    private fun refreshApps() {
        apps = runCatching {
            packageManager.getInstalledPackages(PackageManager.MATCH_ALL)
                .asSequence()
                .filter { it.packageName != packageName }
                .map { info ->
                    AppChoice(
                        label = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: info.packageName,
                        packageName = info.packageName,
                    )
                }
                .sortedWith(compareBy<AppChoice>({ it.label.lowercase(Locale.ROOT) }, { it.packageName }))
                .toList()
        }.getOrElse { emptyList() }
        appSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps)
    }

    private fun refreshWorkspaces() {
        val raw = shizuku.listUsers()
        if (raw == null) {
            status.text = "Workspace service not ready yet"
            return
        }
        workspaces = USER_LINE.findAll(raw).mapNotNull { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (id == 0) return@mapNotNull null
            Workspace(id = id, name = match.groupValues[2].trim().ifBlank { "User $id" }, raw = match.value)
        }.toList()
        workspaceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workspaces)
        status.text = "Workspaces discovered: ${workspaces.size}"
    }

    private fun createWorkspace(clone: Boolean) {
        val state = runtime.probe()
        if (!state.binderAlive || !state.permissionGranted) {
            status.text = "Shizuku permission is required"
            return
        }
        val name = profileName.text.toString().trim().ifBlank { if (clone) "Clone" else "Managed" }
        status.text = "Creating ${if (clone) "Clone profile" else "Managed profile"}…"
        Thread {
            val result = shizuku.createProfile(name, clone)
            runOnUiThread {
                status.text = result ?: "Workspace service is not ready"
                if (result?.contains("Success", ignoreCase = true) == true) refreshWorkspaces()
            }
        }.start()
    }

    private fun installSelectedApp() {
        val ws = selectedWorkspace() ?: run {
            status.text = "Select a workspace first"
            return
        }
        val app = apps.getOrNull(appSpinner.selectedItemPosition) ?: run {
            status.text = "Select an application first"
            return
        }
        Thread {
            val rc = shizuku.installExistingPackage(app.packageName, ws.id)
            runOnUiThread {
                status.text = "installExistingPackage\npackage=${app.packageName}\nuser=${ws.id}\nresult=${rc ?: "service-not-ready"}"
            }
        }.start()
    }

    private fun selectedWorkspace(): Workspace? = workspaces.getOrNull(workspaceSpinner.selectedItemPosition)

    private fun runWorkspaceAction(label: String, ws: Workspace, action: () -> Int?) {
        Thread {
            val rc = action()
            runOnUiThread {
                status.text = "$label workspace user=${ws.id}: ${rc ?: "service-not-ready"}"
                if (rc == 0 && label == "remove") refreshWorkspaces()
            }
        }.start()
    }

    companion object {
        private const val REQ_SHIZUKU = 5401
        private val USER_LINE = Regex("UserInfo\\{(\\d+):([^:}]+)")
    }
}
