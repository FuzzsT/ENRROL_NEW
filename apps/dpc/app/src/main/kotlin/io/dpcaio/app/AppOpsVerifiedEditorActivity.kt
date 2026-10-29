package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.util.concurrent.atomic.AtomicBoolean

class AppOpsVerifiedEditorActivity : Activity() {
    companion object {
        private val mutationInProgress = AtomicBoolean(false)
        private val allowedModes = listOf("allow", "ignore", "deny", "default", "foreground")
    }

    private lateinit var packageNameField: EditText
    private lateinit var userIdField: EditText
    private lateinit var opField: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var output: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Verified AppOps editor"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Verified AppOps editor",
                subtitle = "Read the current package AppOps state, apply one validated mode through Shizuku, then read it back before reporting success.",
                eyebrow = "Permissions",
            )
        )

        body.addView(ModernUi.section(this, "Target"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                packageNameField = ModernUi.field(this@AppOpsVerifiedEditorActivity, "Package name")
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
        output.text = "Reading AppOps..."
        Thread {
            val snapshot = shizuku.getAppOps(pkg, userId)
            postUi { output.text = snapshot ?: "Shizuku unavailable or AppOps read failed" }
        }.start()
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
                postUi { output.text = report }
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
