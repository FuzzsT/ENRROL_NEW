package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class AioDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val body = LinearLayout(this)
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val native = NativeSupportManager.probe(this)
        val preferences = DpcUiPreferences.read(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "DPC-AIO",
                subtitle = "v$versionName • Device Owner / policy / research toolkit",
                eyebrow = "Normal control center",
            )
        )
        body.addView(
            ModernUi.statusCard(
                this,
                "Runtime capabilities",
                native.summary(),
                if (native.hasPolicyOwner || native.hasBinderBridge) ModernUi.Tone.SUCCESS else ModernUi.Tone.NEUTRAL,
            )
        )

        addSection(body, "Start here")
        add(body, "APK / APKS Installer • install / update / export", { AppPackageManagerActivity::class.java }) {
            it.putExtra(AppPackageManagerActivity.EXTRA_MODE, AppPackageManagerActivity.MODE_INSTALLER)
        }
        add(body, "Activity Launcher • apps / favorites / recent") { ActivityLauncherHubActivity::class.java }
        add(body, "Update Center • check / verify / recover") { SelfUpdateCenterActivity::class.java }
        add(body, "Settings • PIN / interface / native support") { DpcSettingsActivity::class.java }

        addSection(body, "Device management")
        add(body, "Control Center • functions / modules / bridges") { ControlCenterActivity::class.java }
        add(body, "Enterprise Policy Hub") { EnterprisePolicyHubActivity::class.java }
        add(body, "Enterprise Operations Center") { EnterpriseOperationsActivity::class.java }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "App Lock") { AppLockActivity::class.java }
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }

        addSection(body, "Setup & enrollment")
        add(body, "Enrollment Engine") { EnrollmentManualActivity::class.java }
        add(body, "Full Offline Setup") { OfflineSetupActivity::class.java }
        add(body, "Enrollment Status") { EnrollmentStatusActivity::class.java }

        if (preferences.showAdvancedHome || preferences.developerMode) {
            addSection(body, "Advanced")
            add(body, "Self Update • detailed updater") { SelfUpdateActivity::class.java }
            if (preferences.nativeJarCallingEnabled && native.jarCallingAvailable) {
                add(body, "Native JAR Runner • app_process") { NativeJarRunnerActivity::class.java }
            }
            add(body, "Module Center (${DpcModuleRegistry.modules.size})") { ModuleCenterActivity::class.java }
            add(body, "File Manager") { FileManagerActivity::class.java }
            add(body, "Autostart Manager") { AutostartManagerActivity::class.java }
            add(body, "APK / APKS Updater", { AppPackageManagerActivity::class.java }) {
                it.putExtra(AppPackageManagerActivity.EXTRA_MODE, AppPackageManagerActivity.MODE_UPDATER)
            }
            add(body, "Activity Explorer • direct package mode") { ActivityExplorerActivity::class.java }
            add(body, "Verified AppOps Editor") { AppOpsVerifiedEditorActivity::class.java }
            add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
            add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }
            add(body, "Knox Enterprise Center") { KnoxEnterpriseCenterActivity::class.java }
            add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }
            add(body, "Certificate & Credential Center") { CredentialCenterActivity::class.java }
            add(body, "Device Lifecycle Center") { DeviceLifecycleActivity::class.java }
            add(body, "Work Profile / COPE") { WorkProfileCopeActivity::class.java }
            add(body, "Engineering Intent Center") { EngineeringIntentCenterActivity::class.java }
            add(body, "Diagnostics") { DpcDiagnosticsActivity::class.java }
            add(body, "PIN Guard") { PinGuardActivity::class.java }
        }

        if (preferences.developerMode) {
            addSection(body, "Lab")
            add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
            add(body, "NFC Lab") { NfcLabActivity::class.java }
        }

        setContentView(ModernUi.page(this, body))
    }

    private fun addSection(root: LinearLayout, label: String) {
        root.addView(ModernUi.section(this, label))
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        add(root, label, target) { }
    }

    private fun add(
        root: LinearLayout,
        label: String,
        target: () -> Class<out Activity>,
        configure: (Intent) -> Unit,
    ) {
        root.addView(
            ModernUi.compactCard(this).apply {
                addView(
                    ModernUi.action(this@AioDashboardActivity, label) {
                        val next = Intent(this@AioDashboardActivity, target())
                        configure(next)
                        startActivity(next)
                    }
                )
            }
        )
    }
}
