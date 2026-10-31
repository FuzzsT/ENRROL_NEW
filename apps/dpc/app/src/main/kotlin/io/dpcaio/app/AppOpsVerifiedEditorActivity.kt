package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.util.concurrent.atomic.AtomicBoolean

class AppOpsVerifiedEditorActivity : Activity() {
    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val AUTO_DISCOVERY_DELAY_MS = 450L
        private val mutationInProgress = AtomicBoolean(false)
        private val allowedModes = listOf("allow", "ignore", "deny", "default", "foreground")
        private val unreadableSnapshots = setOf("INVALID_ARGUMENT", "TIMEOUT", "EXECUTION_ERROR")
        private val appOpNamePattern = Regex("[A-Za-z0-9_.:-]+")
    }

    private enum class ModeFilter { ALL, ALLOW, IGNORE, DENY, DEFAULT, FOREGROUND }

    private data class AppOpEntry(
        val name: String,
        val mode: String,
        val detail: String,
    )

    private data class AppOpRead(
        val canonicalName: String?,
        val uidMode: String?,
        val packageMode: String?,
        val defaultMode: String?,
    )

    private lateinit var packageNameField: EditText
    private lateinit var userIdField: EditText
    private lateinit var opField: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var discoveryFilter: EditText
    private lateinit var modeFilterBar: LinearLayout
    private lateinit var discoveredContainer: LinearLayout
    private lateinit var output: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private var discoveredOps: List<AppOpEntry> = emptyList()
    private var currentModeFilter = ModeFilter.ALL
    @Volatile
    private var discoveryGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Verified AppOps editor"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Verified AppOps editor",
                subtitle = "Discover current AppOps as editable tiles, filter by state, apply one validated mode through Shizuku, then read it back before reporting success.",
                eyebrow = "Permissions",
            )
        )

        body.addView(ModernUi.section(this, "Target"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                packageNameField = ModernUi.field(this@AppOpsVerifiedEditorActivity, "Package name").apply {
                    setText(intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty())
                }
                userIdField = ModernUi.field(this@AppOpsVerifiedEditorActivity, "User ID", InputType.TYPE_CLASS_NUMBER).apply {
                    setText(AndroidUserId.fromUid(Process.myUid()).toString())
                }
                opField = ModernUi.field(this@AppOpsVerifiedEditorActivity, "AppOp name, e.g. CAMERA")
                modeSpinner = Spinner(this@AppOpsVerifiedEditorActivity).apply {
                    adapter = ArrayAdapter(
                        this@AppOpsVerifiedEditorActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        allowedModes,
                    )
                    contentDescription = "AppOp mode"
                }
                addView(packageNameField)
                addView(userIdField)
                addView(
                    ModernUi.actionRow(
                        this@AppOpsVerifiedEditorActivity,
                        ModernUi.action(this@AppOpsVerifiedEditorActivity, "Choose package") {
                            RuntimeTargetPicker.showPackagePicker(
                                this@AppOpsVerifiedEditorActivity,
                                shizuku,
                                targetUserIdOrNull() ?: AndroidUserId.fromUid(Process.myUid()),
                                packageNameField.text.toString().trim(),
                            ) { selected ->
                                packageNameField.setText(selected)
                                readCurrent()
                            }
                        },
                        ModernUi.action(this@AppOpsVerifiedEditorActivity, "Choose user") {
                            val current = AndroidUserId.fromUid(Process.myUid())
                            RuntimeTargetPicker.showUserPicker(
                                this@AppOpsVerifiedEditorActivity,
                                shizuku,
                                current,
                                targetUserIdOrNull() ?: current,
                            ) { selected ->
                                userIdField.setText(selected.toString())
                                if (packageNameField.text.isNotBlank()) readCurrent()
                            }
                        },
                    )
                )
                addView(opField)
                addView(ModernUi.body(this@AppOpsVerifiedEditorActivity, "AppOp mode"))
                addView(modeSpinner)
                addView(
                    ModernUi.actionRow(
                        this@AppOpsVerifiedEditorActivity,
                        ModernUi.action(this@AppOpsVerifiedEditorActivity, "Read AppOps") { readCurrent() },
                        ModernUi.action(this@AppOpsVerifiedEditorActivity, "Apply AppOp + verify", primary = true) { applyAndVerify() },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Discovered AppOps"))
        body.addView(
            ModernUi.card(this).apply {
                discoveryFilter = ModernUi.field(this@AppOpsVerifiedEditorActivity, "Filter AppOps")
                discoveryFilter.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderDiscoveredOps()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(discoveryFilter)

                modeFilterBar = LinearLayout(this@AppOpsVerifiedEditorActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(modeFilterBar)
                renderModeFilters()

                addView(ModernUi.spacer(this@AppOpsVerifiedEditorActivity, 8))
                addView(
                    ModernUi.action(this@AppOpsVerifiedEditorActivity, "Discover / refresh AppOps") {
                        readCurrent()
                    }
                )
                discoveredContainer = LinearLayout(this@AppOpsVerifiedEditorActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ModernUi.body(this@AppOpsVerifiedEditorActivity, "Enter a package and discover its current AppOps."))
                }
                addView(discoveredContainer)
            }
        )

        body.addView(ModernUi.section(this, "Result"))
        output = TextView(this).apply {
            text = "Ready"
            setTextIsSelectable(true)
        }
        body.addView(ModernUi.outputCard(this, output))

        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.title(this@AppOpsVerifiedEditorActivity, "Transaction model"))
                addView(
                    ModernUi.body(
                        this@AppOpsVerifiedEditorActivity,
                        "The editor uses typed Shizuku AppOps APIs only. The requested operation is first resolved to the controlling runtime switch operation. Unreadable state, resolution failure or a UID-level override aborts before mutation. Readback and rollback use that same switch operation.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        shizuku.bind()
        if (packageNameField.text.isNotBlank()) {
            output.text = "Target loaded: ${packageNameField.text}. Discovering AppOps…"
            discoveredContainer.postDelayed({
                if (!isFinishing && !isDestroyed && packageNameField.text.isNotBlank()) readCurrent()
            }, AUTO_DISCOVERY_DELAY_MS)
        }
    }

    private fun targetUserIdOrNull(): Int? = userIdField.text.toString().trim().toIntOrNull()
        ?.takeIf { it >= 0 }

    private fun renderModeFilters() {
        if (!::modeFilterBar.isInitialized) return
        modeFilterBar.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ModeFilter.entries.forEach { filter ->
            val tone = when (filter) {
                ModeFilter.ALLOW, ModeFilter.FOREGROUND -> ModernUi.Tone.SUCCESS
                ModeFilter.IGNORE, ModeFilter.DENY -> ModernUi.Tone.WARNING
                ModeFilter.ALL, ModeFilter.DEFAULT -> ModernUi.Tone.NEUTRAL
            }
            val pill = ModernUi.pill(this, filter.name, filter == currentModeFilter, tone) {
                currentModeFilter = filter
                renderModeFilters()
                renderDiscoveredOps()
            }
            pill.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = ModernUi.dp(this@AppOpsVerifiedEditorActivity, 8) }
            row.addView(pill)
        }
        modeFilterBar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
        )
    }

    private fun readCurrent() {
        val pkg = packageNameField.text.toString().trim()
        val userId = targetUserIdOrNull()
        if (pkg.isBlank()) {
            output.text = "Package name required"
            return
        }
        if (userId == null) {
            output.text = "User ID must be a non-negative integer"
            return
        }
        val generation = ++discoveryGeneration
        output.text = "Reading AppOps..."
        Thread {
            val snapshot = shizuku.getAppOps(pkg, userId)
            val parsed = parseAppOps(snapshot)
            postUi {
                if (generation != discoveryGeneration) return@postUi
                discoveredOps = parsed
                renderDiscoveredOps()
                output.text = if (!snapshotReadable(snapshot)) {
                    "Shizuku unavailable or AppOps read failed: ${snapshot ?: "<null>"}"
                } else {
                    "Discovered ${parsed.size} explicit AppOps for $pkg\n\n$snapshot"
                }
            }
        }.start()
    }

    private fun parseAppOps(snapshot: String?): List<AppOpEntry> {
        if (!snapshotReadable(snapshot)) return emptyList()
        return snapshot.orEmpty().lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val name = line.substring(0, colon).trim()
            val tail = line.substring(colon + 1).trim()
            val mode = tail.substringBefore(' ').substringBefore(';').trim().lowercase()
            if (name.isBlank() || mode !in allowedModes) return@mapNotNull null
            AppOpEntry(name = name, mode = mode, detail = tail)
        }.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }.toList()
    }

    private fun renderDiscoveredOps() {
        if (!::discoveredContainer.isInitialized) return
        val filter = if (::discoveryFilter.isInitialized) discoveryFilter.text.toString().trim().lowercase() else ""
        val modeFilter = currentModeFilter.name.lowercase()
        val rows = discoveredOps.filter { entry ->
            val matchesText = filter.isBlank() || entry.name.lowercase().contains(filter) || entry.mode.contains(filter) || entry.detail.lowercase().contains(filter)
            val matchesMode = currentModeFilter == ModeFilter.ALL || entry.mode == modeFilter
            matchesText && matchesMode
        }
        discoveredContainer.removeAllViews()
        if (rows.isEmpty()) {
            discoveredContainer.addView(ModernUi.body(this, if (discoveredOps.isEmpty()) "No explicit AppOps discovered" else "No matching AppOps"))
            return
        }
        rows.forEach { entry ->
            val tone = when (entry.mode) {
                "allow", "foreground" -> ModernUi.Tone.SUCCESS
                "ignore", "deny" -> ModernUi.Tone.WARNING
                else -> ModernUi.Tone.NEUTRAL
            }
            discoveredContainer.addView(
                ModernUi.compactCard(this, tone).apply {
                    isClickable = true
                    isFocusable = true
                    addView(ModernUi.title(this@AppOpsVerifiedEditorActivity, entry.name))
                    addView(ModernUi.body(this@AppOpsVerifiedEditorActivity, "Current mode: ${entry.mode}\n${entry.detail}\nTap to edit"))
                    addView(ModernUi.chip(this@AppOpsVerifiedEditorActivity, entry.mode.uppercase(), tone))
                    setOnClickListener { selectDiscoveredOp(entry) }
                }
            )
        }
    }

    private fun selectDiscoveredOp(entry: AppOpEntry) {
        opField.setText(entry.name)
        val index = allowedModes.indexOf(entry.mode)
        if (index >= 0) modeSpinner.setSelection(index)
        output.text = "Selected ${entry.name}=${entry.mode}. Choose a mode and tap Apply AppOp + verify."
    }

    private fun applyAndVerify() {
        val pkg = packageNameField.text.toString().trim()
        val op = opField.text.toString().trim()
        val mode = modeSpinner.selectedItem?.toString().orEmpty()
        val userId = targetUserIdOrNull()
        if (pkg.isBlank() || op.isBlank()) {
            output.text = "Package name and AppOp name are required"
            return
        }
        if (userId == null) {
            output.text = "User ID must be a non-negative integer"
            return
        }
        if (mode !in allowedModes) {
            output.text = "Unsupported AppOp mode: $mode"
            return
        }
        if (!mutationInProgress.compareAndSet(false, true)) {
            output.text = "Another AppOps transaction is already running"
            return
        }

        output.text = "Applying $op=$mode..."
        Thread {
            try {
                val resolved = shizuku.resolveAppOp(op)
                val switchOp = resolvedSwitchOp(resolved)
                if (switchOp == null) {
                    postUi { output.text = "AppOp switch resolution failed; mutation aborted: ${resolved ?: "<null>"}" }
                    return@Thread
                }

                val before = shizuku.getAppOp(pkg, switchOp, userId)
                if (!snapshotReadable(before)) {
                    postUi { output.text = "Initial AppOps snapshot unreadable; mutation aborted: ${before ?: "<null>"}" }
                    return@Thread
                }
                val beforeState = parseSingleAppOp(before)
                if (beforeState == null || effectiveMode(beforeState) == null) {
                    postUi { output.text = "Initial AppOps snapshot could not be parsed; mutation aborted\n\n$before" }
                    return@Thread
                }
                if (beforeState.uidMode != null) {
                    postUi {
                        output.text = "UID-level AppOps override present (${beforeState.uidMode}); package-level mutation aborted\n\n$before"
                    }
                    return@Thread
                }

                val beforeMode = effectiveMode(beforeState)
                val rollbackMode = beforeState.packageMode ?: beforeState.defaultMode
                if (rollbackMode == null) {
                    postUi { output.text = "Rollback state unavailable; mutation aborted\n\n$before" }
                    return@Thread
                }

                val exit = shizuku.setAppOp(pkg, switchOp, mode, userId)
                val after = shizuku.getAppOp(pkg, switchOp, userId)
                val afterState = parseSingleAppOp(after)
                val observed = effectiveMode(afterState)
                val verified = exit == 0 && snapshotReadable(after) && requestedModeMatches(afterState, mode)

                var rollbackExit: Int? = null
                var rollbackVerified: Boolean? = null
                var rollbackSnapshot: String? = null
                if (!verified && exit == 0) {
                    rollbackExit = shizuku.setAppOp(pkg, switchOp, rollbackMode, userId)
                    rollbackSnapshot = shizuku.getAppOp(pkg, switchOp, userId)
                    val rollbackState = parseSingleAppOp(rollbackSnapshot)
                    rollbackVerified = rollbackExit == 0 && snapshotReadable(rollbackSnapshot) && requestedModeMatches(rollbackState, rollbackMode)
                }

                val discoverySnapshot = shizuku.getAppOps(pkg, userId)
                val parsedFinal = parseAppOps(discoverySnapshot)
                val report = buildString {
                    appendLine("package=$pkg")
                    appendLine("user=$userId")
                    appendLine("requestedOp=$op")
                    appendLine("switchOp=$switchOp")
                    appendLine("canonicalOp=${afterState?.canonicalName ?: beforeState.canonicalName ?: switchOp}")
                    appendLine("beforeMode=${beforeMode ?: "<unparsed>"}")
                    appendLine("beforePackageMode=${beforeState.packageMode ?: "<implicit-default>"}")
                    appendLine("defaultMode=${beforeState.defaultMode ?: afterState?.defaultMode ?: "<unparsed>"}")
                    appendLine("requested=$mode")
                    appendLine("exit=$exit")
                    appendLine("observed=${observed ?: "<unparsed>"}")
                    appendLine("verified=$verified")
                    if (rollbackExit != null) {
                        appendLine("rollbackMode=$rollbackMode")
                        appendLine("rollbackExit=$rollbackExit")
                        appendLine("rollbackVerified=$rollbackVerified")
                    }
                    appendLine("resolution=${resolved ?: "<unavailable>"}")
                    appendLine("before=$before")
                    appendLine("after=${after ?: "<unavailable>"}")
                    if (rollbackSnapshot != null) appendLine("rollbackAfter=$rollbackSnapshot")
                }
                postUi {
                    if (snapshotReadable(discoverySnapshot)) {
                        discoveredOps = parsedFinal
                        renderDiscoveredOps()
                    }
                    output.text = report
                }
            } finally {
                mutationInProgress.set(false)
            }
        }.start()
    }

    private fun snapshotReadable(snapshot: String?): Boolean {
        val normalized = snapshot?.trim().orEmpty()
        return normalized.isNotEmpty() && normalized !in unreadableSnapshots
    }

    private fun resolvedSwitchOp(resolved: String?): String? {
        if (!snapshotReadable(resolved)) return null
        return resolved.orEmpty().lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("switch=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.matches(appOpNamePattern) }
    }

    private fun parseSingleAppOp(snapshot: String?): AppOpRead? {
        if (!snapshotReadable(snapshot)) return null
        var canonicalName: String? = null
        var uidMode: String? = null
        var packageMode: String? = null
        var defaultMode: String? = null

        snapshot.orEmpty().lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("Uid mode:", ignoreCase = true) -> {
                    parseNamedMode(line.substringAfter("Uid mode:").trim())?.let { (name, mode) ->
                        canonicalName = name
                        uidMode = mode
                    }
                }
                line.startsWith("Default mode:", ignoreCase = true) -> {
                    defaultMode = line.substringAfter("Default mode:").trim().lowercase().takeIf { it in allowedModes }
                }
                else -> {
                    parseNamedMode(line)?.let { (name, mode) ->
                        canonicalName = canonicalName ?: name
                        packageMode = mode
                    }
                }
            }
        }

        if (uidMode == null && packageMode == null && defaultMode == null) return null
        return AppOpRead(
            canonicalName = canonicalName,
            uidMode = uidMode,
            packageMode = packageMode,
            defaultMode = defaultMode,
        )
    }

    private fun parseNamedMode(line: String): Pair<String, String>? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val name = line.substring(0, colon).trim()
        val tail = line.substring(colon + 1).trim()
        val mode = tail.substringBefore(' ').substringBefore(';').trim().lowercase()
        if (name.isBlank() || mode !in allowedModes) return null
        return name to mode
    }

    private fun effectiveMode(read: AppOpRead?): String? {
        if (read == null) return null
        read.uidMode?.let { return it }
        read.packageMode?.let { return it }
        return read.defaultMode
    }

    private fun requestedModeMatches(read: AppOpRead?, requested: String): Boolean {
        if (read == null || read.uidMode != null) return false
        return if (requested == "default") {
            read.packageMode == "default" || (read.packageMode == null && read.defaultMode == "default")
        } else {
            effectiveMode(read) == requested
        }
    }

    private fun postUi(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }
}
