package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DpcPinSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC App PIN"
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val configured = DpcPinManager.isConfigured(this)
        val enabled = DpcPinManager.isEnabled(this)

        root.addView(TextView(this).apply {
            text = when {
                enabled -> "App PIN: ENABLED\nThe DPC dashboard requires the configured PIN."
                configured -> "App PIN: DISABLED\nA PIN is stored and can be re-enabled."
                else -> "App PIN: NOT CONFIGURED"
            }
        })

        addButton(root, if (configured) "Change PIN" else "Set PIN") { showSetOrChangeDialog(configured) }

        if (configured) {
            addButton(root, if (enabled) "Disable PIN" else "Enable PIN") {
                showVerifyDialog(if (enabled) "Disable PIN" else "Enable PIN") {
                    DpcPinManager.setEnabled(this, !enabled)
                    toast(if (enabled) "PIN disabled" else "PIN enabled")
                    render()
                }
            }
            addButton(root, "Remove PIN") {
                showVerifyDialog("Remove PIN") {
                    DpcPinManager.clearPin(this)
                    toast("PIN removed")
                    render()
                }
            }
        }

        if (enabled) {
            addButton(root, "Lock DPC now") {
                DpcPinSession.lock()
                startActivity(Intent(this, AioDashboardActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
        }

        root.addView(TextView(this).apply {
            text = "PIN protects only the human-facing DPC application UI. Android provisioning callbacks remain available so work-profile and fully-managed enrollment are not blocked. After 5 incorrect attempts, PIN entry is delayed for 30 seconds."
            setPadding(0, 24, 0, 0)
        })

        setContentView(DpcUiShell.scroll(this, root))
    }

    private fun showSetOrChangeDialog(requireCurrent: Boolean) {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val current = pinField("Current PIN").also { if (requireCurrent) body.addView(it) }
        val next = pinField("New PIN").also { body.addView(it) }
        val confirm = pinField("Confirm new PIN").also { body.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (requireCurrent) "Change DPC PIN" else "Set DPC PIN")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (requireCurrent && !DpcPinManager.verify(this, current.text.toString())) {
                    showPinError(current)
                    return@setOnClickListener
                }
                val newPin = next.text.toString()
                val error = DpcPinManager.validateFormat(newPin)
                if (error != null) {
                    next.error = error
                    return@setOnClickListener
                }
                if (newPin != confirm.text.toString()) {
                    confirm.error = "PINs do not match"
                    return@setOnClickListener
                }
                DpcPinManager.setPin(this, newPin, enabled = true)
                toast("PIN saved and enabled")
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }

    private fun showVerifyDialog(title: String, onVerified: () -> Unit) {
        val input = pinField("Current PIN")
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!DpcPinManager.verify(this, input.text.toString())) {
                    showPinError(input)
                    return@setOnClickListener
                }
                dialog.dismiss()
                onVerified()
            }
        }
        dialog.show()
    }

    private fun showPinError(input: EditText) {
        val remaining = DpcPinManager.blockedRemainingMs(this)
        input.error = if (remaining > 0L) {
            "Too many attempts. Try again in ${(remaining + 999) / 1000}s"
        } else {
            "Incorrect PIN"
        }
        input.setText("")
        input.requestFocus()
    }

    private fun pinField(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        maxLines = 1
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
