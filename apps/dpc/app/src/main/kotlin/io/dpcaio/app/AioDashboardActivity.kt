package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class AioDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"
        showProtectedContent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        showProtectedContent()
    }

    override fun onResume() {
        super.onResume()
        if (DpcPinManager.isEnabled(this) && !DpcPinSession.isUnlocked()) {
            showPinUnlock()
        }
    }

    private fun showProtectedContent() {
        if (DpcPinManager.isEnabled(this) && !DpcPinSession.isUnlocked()) {
            showPinUnlock()
        } else {
            showDashboard()
        }
    }

    private fun showPinUnlock() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 48, 24, 24)
        }
        body.addView(TextView(this).apply {
            text = "DPC-AIO is locked\nEnter the application PIN to continue."
            setTypeface(typeface, Typeface.BOLD)
        })
        val pin = EditText(this).apply {
            hint = "DPC PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }
        body.addView(pin)
        body.addView(Button(this).apply {
            text = "Unlock"
            setOnClickListener {
                val remaining = DpcPinManager.blockedRemainingMs(this@AioDashboardActivity)
                if (remaining > 0L) {
                    pin.error = "Too many attempts. Try again in ${(remaining + 999) / 1000}s"
                    return@setOnClickListener
                }
                if (DpcPinManager.verify(this@AioDashboardActivity, pin.text.toString())) {
                    DpcPinSession.markUnlocked()
                    showDashboard()
                } else {
                    val wait = DpcPinManager.blockedRemainingMs(this@AioDashboardActivity)
                    pin.error = if (wait > 0L) {
                        "Too many attempts. Try again in ${(wait + 999) / 1000}s"
                    } else {
                        "Incorrect PIN"
                    }
                    pin.setText("")
                    pin.requestFocus()
                }
            }
        })
        DpcUiShell.install(this, body)
        setContentView(body)
    }

    private fun showDashboard() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingDp(24, 24, 24, 24) }
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "unknown"
        body.addView(TextView(this).apply {
            text = "DPC-AIO $versionName\nEnterprise provisioning, policy, app/component and diagnostics toolkit"
            setTypeface(typeface, Typeface.BOLD)
        })

        section(body, "Enrollment")
        add(body, "Enrollment Engine") { EnrollmentManualActivity::class.java }
        add(body, "Full Offline Setup") { OfflineSetupActivity::class.java }
        add(body, "Enrollment Status") { EnrollmentStatusActivity::class.java }

        section(body, "Apps & Components")
        add(body, "Activity Manager 3.0") { ActivityExplorerActivity::class.java }
        addIntent(body, "Favorites") { Intent(this, ActivityExplorerActivity::class.java).putExtra("favoritesOnly", true) }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "Module Center (${DpcModuleRegistry.modules.size})") { ModuleCenterActivity::class.java }

        section(body, "Device & Policy")
        add(body, "Enterprise Policy Hub") { EnterprisePolicyHubActivity::class.java }
        add(body, "Enterprise Operations Center") { EnterpriseOperationsActivity::class.java }
        add(body, "Device Lifecycle Center") { DeviceLifecycleActivity::class.java }

        section(body, "Security & Credentials")
        add(body, "App PIN / Security") { DpcPinSettingsActivity::class.java }
        add(body, "Certificate & Credential Center") { CredentialCenterActivity::class.java }
        add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }

        section(body, "Network")
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }

        section(body, "Work Profile / COPE")
        add(body, "Work Profile / COPE") { WorkProfileCopeActivity::class.java }

        section(body, "OEM / Knox")
        add(body, "Knox Enterprise Center") { KnoxEnterpriseCenterActivity::class.java }
        add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
        add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }

        section(body, "Diagnostics")
        add(body, "Diagnostics") { DpcDiagnosticsActivity::class.java }

        val preferences = DpcUiPreferences.read(this)
        if (preferences.developerMode) {
            section(body, "Advanced / Lab")
            add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
            add(body, "NFC Lab") { NfcLabActivity::class.java }
            add(body, "Verification Toggle") { VerificationToggleActivity::class.java }
        }

        setContentView(DpcUiShell.scroll(this, body))
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPaddingDp(0, 20, 0, 4)
        })
    }

    private fun addIntent(root: LinearLayout, label: String, intentFactory: () -> Intent) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
            setPaddingDp(16, 10, 16, 10)
            setOnClickListener { startActivity(intentFactory()) }
        })
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
            setPaddingDp(16, 10, 16, 10)
            setOnClickListener { startActivity(Intent(this@AioDashboardActivity, target())) }
        })
    }
}
