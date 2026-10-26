package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AioDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "unknown"
        body.addView(TextView(this).apply { text = "DPC-AIO $versionName\nDevice Owner / policy / research toolkit" })

        add(body, "Enrollment Engine") { EnrollmentManualActivity::class.java }
        add(body, "Full Offline Setup") { OfflineSetupActivity::class.java }
        add(body, "Enrollment Status") { EnrollmentStatusActivity::class.java }
        add(body, "Enterprise Policy Hub") { EnterprisePolicyHubActivity::class.java }
        add(body, "Enterprise Operations Center") { EnterpriseOperationsActivity::class.java }
        add(body, "Certificate & Credential Center") { CredentialCenterActivity::class.java }
        add(body, "Device Lifecycle Center") { DeviceLifecycleActivity::class.java }
        add(body, "Work Profile / COPE") { WorkProfileCopeActivity::class.java }
        add(body, "Knox Enterprise Center") { KnoxEnterpriseCenterActivity::class.java }
        add(body, "Module Center (${DpcModuleRegistry.modules.size})") { ModuleCenterActivity::class.java }
        add(body, "Activity Explorer") { ActivityExplorerActivity::class.java }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
        add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }
        add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }
        add(body, "Diagnostics") { DpcDiagnosticsActivity::class.java }

        val preferences = DpcUiPreferences.read(this)
        if (preferences.developerMode) {
            body.addView(TextView(this).apply {
                text = "Advanced / Lab"
                setTypeface(typeface, Typeface.BOLD)
            })
            add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
            add(body, "NFC Lab") { NfcLabActivity::class.java }
        }

        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        root.addView(Button(this).apply {
            text = label
            setOnClickListener { startActivity(Intent(this@AioDashboardActivity, target())) }
        })
    }
}
