package io.dpcaio.app

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
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

    private data class BatchPlanEntry(val index: Int, val name: String)

    private data class BatchPlan(
        val app: AppChoice,
        val cloneProfile: Boolean,
        val baseName: String,
        val count: Int,
        val entries: List<BatchPlanEntry>,
        val fingerprint: String,
    )

    private enum class BatchOutcome { SUCCESS, PARTIAL, FAILED, SKIPPED }

    private data class BatchResult(
        val entry: BatchPlanEntry,
        val outcome: BatchOutcome,
        val userId: Int?,
        val detail: String,
    )

    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var workspaceSpinner: Spinner
    private lateinit var appSpinner: Spinner
    private lateinit var profileName: EditText
    private lateinit var batchBaseName: EditText
    private lateinit var batchCount: EditText
    private lateinit var batchTypeSpinner: Spinner
    private lateinit var batchPreview: TextView

    private val runtime = AndroidShizukuRuntime()
    private lateinit var shizuku: ShizukuUserServiceClient
    private var workspaces: List<Workspace> = emptyList()
    private var apps: List<AppChoice> = emptyList()
    private var currentPlan: BatchPlan? = null
    private var batchResults: List<BatchResult> = emptyList()
    private val activeBatchCreatedUserIds = linkedSetOf<Int>()
    @Volatile private var destroyed = false

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

        body.addView(ModernUi.section(this, "Multi Clone Manager"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@DualAppWorkspaceActivity, "Preview → Execute creates a bounded batch of isolated profile instances for the selected application. Android/OEM profile limits remain authoritative."))
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                batchBaseName = ModernUi.field(this@DualAppWorkspaceActivity, "Batch profile base name")
                batchBaseName.setText("DualAppBatch")
                addView(batchBaseName)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                batchCount = ModernUi.field(this@DualAppWorkspaceActivity, "Copies (1-$MAX_BATCH_COPIES)")
                batchCount.inputType = InputType.TYPE_CLASS_NUMBER
                batchCount.setText("3")
                addView(batchCount)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                batchTypeSpinner = Spinner(this@DualAppWorkspaceActivity)
                batchTypeSpinner.adapter = ArrayAdapter(
                    this@DualAppWorkspaceActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Managed profile", "Clone profile"),
                )
                addView(batchTypeSpinner)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Preview batch") { previewBatch() })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Execute batch", primary = true) { executeBatch(retryOnly = false) })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Retry failed") { executeBatch(retryOnly = true) })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.action(this@DualAppWorkspaceActivity, "Cleanup batch workspaces") { cleanupBatchWorkspaces() })
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                batchPreview = ModernUi.body(this@DualAppWorkspaceActivity, "No batch preview yet")
                addView(batchPreview)
                addView(ModernUi.spacer(this@DualAppWorkspaceActivity, 8))
                addView(ModernUi.body(this@DualAppWorkspaceActivity, "Retry only re-runs FAILED/PARTIAL entries. Cleanup is explicit and only targets workspaces created by the active batch. APK package names/signatures are never changed."))
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
        destroyed = true
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
        currentPlan = null
        if (::batchPreview.isInitialized) batchPreview.text = "Application inventory changed; preview the batch again"
    }

    private fun refreshWorkspaces() {
        val raw = shizuku.listUsers()
        if (raw == null) {
            status.text = "Workspace service not ready yet"
            return
        }
        workspaces = parseWorkspaces(raw)
        workspaceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workspaces)
        status.text = "Workspaces discovered: ${workspaces.size}"
    }

    private fun parseWorkspaces(raw: String): List<Workspace> = USER_LINE.findAll(raw).mapNotNull { match ->
        val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        if (id == 0) return@mapNotNull null
        Workspace(id = id, name = match.groupValues[2].trim().ifBlank { "User $id" }, raw = match.value)
    }.toList()

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
            if (destroyed) return@Thread
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
        val app = selectedApp() ?: run {
            status.text = "Select an application first"
            return
        }
        Thread {
            val rc = shizuku.installExistingPackage(app.packageName, ws.id)
            if (destroyed) return@Thread
            runOnUiThread {
                status.text = "installExistingPackage\npackage=${app.packageName}\nuser=${ws.id}\nresult=${rc ?: "service-not-ready"}"
            }
        }.start()
    }

    private fun previewBatch() {
        val state = runtime.probe()
        if (!state.binderAlive || !state.permissionGranted) {
            status.text = "Shizuku permission is required before batch preview"
            return
        }
        val app = selectedApp() ?: run {
            status.text = "Select an application before batch preview"
            return
        }
        val count = batchCount.text.toString().trim().toIntOrNull()
        if (count == null || count !in 1..MAX_BATCH_COPIES) {
            status.text = "Copies must be in 1..$MAX_BATCH_COPIES"
            currentPlan = null
            return
        }
        val baseName = batchBaseName.text.toString().trim()
        val cloneProfile = batchTypeSpinner.selectedItemPosition == 1
        val entries = (1..count).map { index -> BatchPlanEntry(index, "$baseName $index") }
        if (baseName.isBlank() || entries.any { !validProfileName(it.name) }) {
            status.text = "Batch profile names must use letters, numbers, spaces, dot, dash or underscore and fit within 48 characters"
            currentPlan = null
            return
        }
        val fingerprint = batchFingerprint(app.packageName, cloneProfile, baseName, count)
        currentPlan = BatchPlan(app, cloneProfile, baseName, count, entries, fingerprint)
        batchResults = emptyList()
        activeBatchCreatedUserIds.clear()
        batchPreview.text = renderPlan(currentPlan!!)
        status.text = "Batch preview ready: $count ${if (cloneProfile) "Clone" else "Managed"} profile(s)"
    }

    private fun executeBatch(retryOnly: Boolean) {
        val plan = currentPlan ?: run {
            status.text = "Preview batch before Execute batch"
            return
        }
        if (currentBatchFingerprint() != plan.fingerprint) {
            currentPlan = null
            status.text = "Batch inputs changed. Preview batch again before Execute batch."
            batchPreview.text = "Preview invalidated by changed inputs"
            return
        }
        val previousByIndex = batchResults.associateBy { it.entry.index }
        status.text = if (retryOnly) "Retrying FAILED/PARTIAL batch entries…" else "Executing batch sequentially…"
        Thread {
            val next = mutableListOf<BatchResult>()
            for (entry in plan.entries) {
                if (destroyed) break
                val previous = previousByIndex[entry.index]
                if (retryOnly && previous != null && previous.outcome !in setOf(BatchOutcome.FAILED, BatchOutcome.PARTIAL)) {
                    next += BatchResult(entry, BatchOutcome.SKIPPED, previous.userId, "Previous ${previous.outcome}; retry not executed")
                    postBatchProgress(plan, next)
                    continue
                }
                next += executeBatchEntry(plan, entry, if (retryOnly) previous else null)
                postBatchProgress(plan, next)
            }
            batchResults = next
            if (!destroyed) runOnUiThread {
                batchPreview.text = renderBatchResults(plan, batchResults)
                status.text = batchSummary(batchResults)
                refreshWorkspaces()
            }
        }.start()
    }

    private fun executeBatchEntry(plan: BatchPlan, entry: BatchPlanEntry, previous: BatchResult?): BatchResult {
        var userId = previous?.userId?.takeIf { profileExists(it) }
        if (userId == null) {
            val created = shizuku.createProfile(entry.name, plan.cloneProfile)
                ?: return BatchResult(entry, BatchOutcome.FAILED, null, "createProfile: service-not-ready")
            userId = CREATED_USER_ID.find(created)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (userId == null) return BatchResult(entry, BatchOutcome.FAILED, null, "createProfile: $created")
            activeBatchCreatedUserIds += userId
        }

        val startRc = shizuku.startProfile(userId)
        if (startRc != 0) return BatchResult(entry, BatchOutcome.PARTIAL, userId, "startProfile=$startRc")

        val installRc = shizuku.installExistingPackage(plan.app.packageName, userId)
        if (installRc != 0) return BatchResult(entry, BatchOutcome.PARTIAL, userId, "installExistingPackage=$installRc")

        val packages = shizuku.listPackagesForUser(userId)
            ?: return BatchResult(entry, BatchOutcome.PARTIAL, userId, "listPackagesForUser=service-not-ready")
        val present = packages.lineSequence().any { it.trim() == "package:${plan.app.packageName}" }
        return if (present) {
            BatchResult(entry, BatchOutcome.SUCCESS, userId, "package verified")
        } else {
            BatchResult(entry, BatchOutcome.PARTIAL, userId, "package not present after install-existing")
        }
    }

    private fun cleanupBatchWorkspaces() {
        val ids = activeBatchCreatedUserIds.toList()
        if (ids.isEmpty()) {
            status.text = "No active-batch workspaces to clean up"
            return
        }
        val packageName = currentPlan?.app?.packageName
        status.text = "Cleaning up ${ids.size} active-batch workspace(s)…"
        Thread {
            val lines = mutableListOf<String>()
            for (userId in ids) {
                if (destroyed) break
                val uninstallRc = if (packageName != null) shizuku.uninstallPackageForUser(packageName, userId) else null
                val stopRc = shizuku.stopProfile(userId)
                val removeRc = shizuku.removeProfile(userId)
                if (removeRc == 0) activeBatchCreatedUserIds.remove(userId)
                lines += "user=$userId uninstall=${uninstallRc ?: "n/a"} stop=${stopRc ?: "n/a"} remove=${removeRc ?: "n/a"}"
            }
            if (!destroyed) runOnUiThread {
                batchPreview.text = "Cleanup batch workspaces\n${lines.joinToString("\n")}"
                status.text = "Cleanup finished; remaining active-batch workspaces=${activeBatchCreatedUserIds.size}"
                refreshWorkspaces()
            }
        }.start()
    }

    private fun postBatchProgress(plan: BatchPlan, results: List<BatchResult>) {
        if (destroyed) return
        runOnUiThread {
            batchPreview.text = renderBatchResults(plan, results)
            status.text = "Batch progress ${results.size}/${plan.count}"
        }
    }

    private fun renderPlan(plan: BatchPlan): String = buildString {
        appendLine("Multi Clone Manager — PREVIEW")
        appendLine("package=${plan.app.packageName}")
        appendLine("profileType=${if (plan.cloneProfile) "Clone profile" else "Managed profile"}")
        appendLine("baseName=${plan.baseName}")
        appendLine("copies=${plan.count}")
        plan.entries.forEach { appendLine("${it.index}. ${it.name}") }
        append("No changes have been applied yet.")
    }

    private fun renderBatchResults(plan: BatchPlan, results: List<BatchResult>): String = buildString {
        appendLine("Multi Clone Manager — RESULTS")
        appendLine("package=${plan.app.packageName}")
        for (result in results) {
            appendLine("${result.entry.index}. ${result.entry.name}: ${result.outcome} user=${result.userId ?: "-"} — ${result.detail}")
        }
    }.trimEnd()

    private fun batchSummary(results: List<BatchResult>): String {
        val success = results.count { it.outcome == BatchOutcome.SUCCESS }
        val partial = results.count { it.outcome == BatchOutcome.PARTIAL }
        val failed = results.count { it.outcome == BatchOutcome.FAILED }
        val skipped = results.count { it.outcome == BatchOutcome.SKIPPED }
        return "Batch finished: SUCCESS=$success PARTIAL=$partial FAILED=$failed SKIPPED=$skipped"
    }

    private fun profileExists(userId: Int): Boolean {
        val raw = shizuku.listUsers() ?: return false
        return parseWorkspaces(raw).any { it.id == userId }
    }

    private fun currentBatchFingerprint(): String? {
        val app = selectedApp() ?: return null
        val count = batchCount.text.toString().trim().toIntOrNull() ?: return null
        val baseName = batchBaseName.text.toString().trim()
        val cloneProfile = batchTypeSpinner.selectedItemPosition == 1
        return batchFingerprint(app.packageName, cloneProfile, baseName, count)
    }

    private fun batchFingerprint(packageName: String, cloneProfile: Boolean, baseName: String, count: Int): String =
        "$packageName|$cloneProfile|$baseName|$count"

    private fun validProfileName(value: String): Boolean = value.length in 1..48 && PROFILE_NAME_PATTERN.matches(value)

    private fun selectedWorkspace(): Workspace? = workspaces.getOrNull(workspaceSpinner.selectedItemPosition)
    private fun selectedApp(): AppChoice? = apps.getOrNull(appSpinner.selectedItemPosition)

    private fun runWorkspaceAction(label: String, ws: Workspace, action: () -> Int?) {
        Thread {
            val rc = action()
            if (destroyed) return@Thread
            runOnUiThread {
                status.text = "$label workspace user=${ws.id}: ${rc ?: "service-not-ready"}"
                if (rc == 0 && label == "remove") refreshWorkspaces()
            }
        }.start()
    }

    companion object {
        private const val REQ_SHIZUKU = 5401
        private const val MAX_BATCH_COPIES = 20
        private val USER_LINE = Regex("UserInfo\\{(\\d+):([^:}]+)")
        private val CREATED_USER_ID = Regex("(?:created user id|user id|id)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        private val PROFILE_NAME_PATTERN = Regex("[A-Za-z0-9 _.-]+")
    }
}
