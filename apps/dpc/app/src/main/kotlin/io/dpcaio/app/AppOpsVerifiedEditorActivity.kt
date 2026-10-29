package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.util.concurrent.atomic.AtomicBoolean

class AppOpsVerifiedEditorActivity : Activity() {
    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        private val mutationInProgress = AtomicBoolean(false)
        private val allowedModes = listOf("allow", "ignore", "deny", "default", "foreground")
    }

    private data class AppOpEntry(
        val name: String,
        val mode: String,
        val detail: String,
    )

    private lateinit var packageNameField: EditText
    private lateinit var userIdField: EditText
    private lateinit var opField: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var discoveryFilter: EditText
    private lateinit var discoveredContainer: LinearLayout
    private lateinit var output: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private var discoveredOps: List<AppOpEntry> = emptyList()
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
                subtitle = "Discover current AppOps as editable tiles, apply one validated mode through Shizuku, then read it back before reporting success.",
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
                        "The editor uses the existing typed Shizuku AppOps API. It does not expose arbitrary shell execution. A command exit code alone is not considered success; the post-write AppOps snapshot must contain the requested mode for the selected operation.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        shizuku.bind()
        if (packageNameField.text.isNotBlank()) {
            output.text = "Target loaded: ${packageNameField.text}. Tap Discover / refresh AppOps."
        }
    }

    private fun targetUserId(): Int = userIdField.text.toString().trim().toIntOrNull()
        ?: AndroidUserId.fromUid(Process.myUid())

    private fun readCurrent() {
        val pkg = packageNameField.text.toString().trim()
        val userId = targetUserId()
        if (pkg.isBlank()) {
            output.text = "Package name required"
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
                output.text = if (snapshot == null) {
                    "Shizuku unavailable or AppOps read failed"
                } else {
                    "Discovered ${parsed.size} AppOps for $pkg\n\n$snapshot"
                }
            }
        }.start()
    }

    private fun parseAppOps(snapshot: String?): List<AppOpEntry> {
        if (snapshot.isNullOrBlank()) return emptyList()
        return snapshot.lineSequence().mapNotNull { rawLine ->
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
        val rows = discoveredOps.filter { entry ->
            filter.isBlank() || entry.name.lowercase().contains(filter) || entry.mode.contains(filter) || entry.detail.lowercase().contains(filter)
        }
        discoveredContainer.removeAllViews()
        if (rows.isEmpty()) {
            discoveredContainer.addView(ModernUi.body(this, if (discoveredOps.isEmpty()) "No AppOps discovered" else "No matching AppOps"))
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
        val userId = targetUserId()
        if (pkg.isBlank() || op.isBlank()) {
            output.text = "Package name and AppOp name are required"
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
                val before = shizuku.getAppOps(pkg, userId)
                val exit = shizuku.setAppOp(pkg, op, mode, userId)
                val after = shizuku.getAppOps(pkg, userId)
                val observed = extractMode(after, op)
                val verified = exit == 0 && observed == mode
                val parsedAfter = parseAppOps(after)
                val report = buildString {
                    appendLine("package=$pkg")
                    appendLine("user=$userId")
                    appendLine("op=$op")
                    appendLine("requested=$mode")
                    appendLine("exit=$exit")
                    appendLine("observed=${observed ?: "<unparsed>"}")
                    appendLine("verified=$verified")
                    appendLine("before=${before ?: "<unavailable>"}")
                    appendLine("after=${after ?: "<unavailable>"}")
                }
                postUi {
                    discoveredOps = parsedAfter
                    renderDiscoveredOps()
                    output.text = report
                }
            } finally {
                mutationInProgress.set(false)
            }
        }.start()
    }

    private fun extractMode(snapshot: String?, op: String): String? {
        val wanted = op.trim()
        if (snapshot.isNullOrBlank() || wanted.isBlank()) return null
        return snapshot.lineSequence().map { it.trim() }.firstNotNullOfOrNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@firstNotNullOfOrNull null
            val name = line.substring(0, colon).trim()
            if (!name.equals(wanted, ignoreCase = true)) return@firstNotNullOfOrNull null
            val tail = line.substring(colon + 1).trim()
            tail.substringBefore(' ').substringBefore(';').trim().lowercase().takeIf { it in allowedModes }
        }
    }

    private fun postUi(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }
}
