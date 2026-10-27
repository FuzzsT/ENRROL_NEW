package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class AioDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"

        val body = LinearLayout(this)
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        body.addView(ModernUi.hero(
            this,
            title = "DPC-AIO",
            subtitle = "v$versionName • Device Owner / policy / research toolkit",
            eyebrow = "Enterprise control center",
        ))

        addSection(body, "Control")
        add(body, "Control Center • Functions / Modules / Shizuku") { ControlCenterActivity::class.java }
        add(body, "Activity Launcher • Apps / Favorites / Recent") { ActivityLauncherHubActivity::class.java }

        addSection(body, "Setup & enrollment")
        add(body, "Enrollment Engine") { EnrollmentManualActivity::class.java }
        add(body, "Full Offline Setup") { OfflineSetupActivity::class.java }
        add(body, "Enrollment Status") { EnrollmentStatusActivity::class.java }

        addSection(body, "Management")
        add(body, "Enterprise Policy Hub") { EnterprisePolicyHubActivity::class.java }
        add(body, "Enterprise Operations Center") { EnterpriseOperationsActivity::class.java }
        add(body, "Certificate & Credential Center") { CredentialCenterActivity::class.java }
        add(body, "Device Lifecycle Center") { DeviceLifecycleActivity::class.java }
        add(body, "Work Profile / COPE") { WorkProfileCopeActivity::class.java }
        add(body, "Knox Enterprise Center") { KnoxEnterpriseCenterActivity::class.java }

        addSection(body, "Tools")
        add(body, "Module Center (${DpcModuleRegistry.modules.size})") { ModuleCenterActivity::class.java }
        add(body, "Activity Explorer • direct package mode") { ActivityExplorerActivity::class.java }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
        add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }
        add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }
        add(body, "Diagnostics") { DpcDiagnosticsActivity::class.java }

        val preferences = DpcUiPreferences.read(this)
        if (preferences.developerMode) {
            addSection(body, "Advanced / Lab")
            add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
            add(body, "NFC Lab") { NfcLabActivity::class.java }
        }

        setContentView(ModernUi.page(this, body))
    }

    private fun addSection(root: LinearLayout, label: String) {
        root.addView(ModernUi.section(this, label))
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        root.addView(ModernUi.compactCard(this).apply {
            addView(ModernUi.action(this@AioDashboardActivity, label) {
                startActivity(Intent(this@AioDashboardActivity, target()))
            })
        })
    }
}
