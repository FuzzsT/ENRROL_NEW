package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import io.dpcaio.policy.DeviceSecurityPolicySpec
import io.dpcaio.policy.FrpPolicySpec
import io.dpcaio.policy.LockTaskPolicySpec
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class DeviceLifecycleActivity : Activity() {
    private lateinit var gateway: AndroidDevicePolicyGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Device Lifecycle Center"
        gateway = AndroidDevicePolicyGateway(this, ComponentName(this, AioDeviceAdminReceiver::class.java))
        render()
    }

    private fun render() {
        val root = ModernUi.page(this)
        root.addView(
            ModernUi.hero(
                this,
                title = "Device Lifecycle Center",
                subtitle = "Kiosk, security, application lifecycle and Factory Reset Protection",
                eyebrow = "HIGH-IMPACT DEVICE POLICY",
            )
        )

        root.addView(ModernUi.section(this, "Kiosk / Lock Task"))
        val kiosk = ModernUi.field(this, "Comma-separated allowed packages")
        val systemInfo = CheckBox(this).apply { text = "System info" }
        val notifications = CheckBox(this).apply { text = "Notifications" }
        val home = CheckBox(this).apply { text = "Home" }
        val overview = CheckBox(this).apply { text = "Overview" }
        val globalActions = CheckBox(this).apply { text = "Global actions" }
        val keyguard = CheckBox(this).apply { text = "Keyguard" }
        root.addView(
            ModernUi.card(this).apply {
                addView(kiosk)
                addView(systemInfo)
                addView(notifications)
                addView(home)
                addView(overview)
                addView(globalActions)
                addView(keyguard)
                addView(ModernUi.spacer(this@DeviceLifecycleActivity, 8))
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Preview Lock Task", primary = true) {
                        var mask = 0
                        if (systemInfo.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                        if (home.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        if (notifications.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        if (overview.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        if (globalActions.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                        if (keyguard.isChecked) mask = mask or DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                        val spec = LockTaskPolicySpec(csv(kiosk.text.toString()), mask)
                        confirm("Preview Lock Task", "Current: ${gateway.getLockTaskPolicySpec()}\nRequested: $spec") {
                            val apply = gateway.setLockTaskPolicySpec(spec)
                            show("Apply=${apply.status}\nReadback=${gateway.getLockTaskPolicySpec()}")
                        }
                    }
                )
            }
        )

        root.addView(ModernUi.section(this, "Device Security"))
        val wipe = ModernUi.field(this, "Failed-password wipe threshold (0 disables)").apply { setText("0") }
        val disableCamera = CheckBox(this).apply { text = "Disable camera" }
        val disableScreenCapture = CheckBox(this).apply { text = "Disable screen capture" }
        val disableBiometrics = CheckBox(this).apply { text = "Disable keyguard biometrics" }
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@DeviceLifecycleActivity, "Password complexity: HIGH"))
                addView(wipe)
                addView(disableCamera)
                addView(disableScreenCapture)
                addView(disableBiometrics)
                addView(ModernUi.spacer(this@DeviceLifecycleActivity, 8))
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Preview / Confirm security policy", destructive = true) {
                        val keyguardMask = if (disableBiometrics.isChecked) DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS else DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
                        val spec = DeviceSecurityPolicySpec(
                            passwordComplexity = DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH,
                            maxFailedPasswordsForWipe = wipe.text.toString().toIntOrNull() ?: 0,
                            keyguardDisabledFeatures = keyguardMask,
                            cameraDisabled = disableCamera.isChecked,
                            screenCaptureDisabled = disableScreenCapture.isChecked,
                        )
                        confirm(
                            "Confirm CRITICAL policy",
                            "Password complexity=HIGH\nFailed-attempt wipe threshold=${spec.maxFailedPasswordsForWipe}\nCamera disabled=${spec.cameraDisabled}\nScreen capture disabled=${spec.screenCaptureDisabled}\n\nAndroid may wipe after the configured number of failed attempts.",
                        ) { show(gateway.setDeviceSecurityPolicySpec(spec).toString()) }
                    }
                )
            }
        )

        root.addView(ModernUi.section(this, "Application Control"))
        val pkg = ModernUi.field(this, "Target package")
        val restrictions = ModernUi.field(this, "Restrictions: key=value;key2=value2")
        val userControl = ModernUi.field(this, "User-control-disabled packages, comma-separated")
        root.addView(
            ModernUi.card(this).apply {
                addView(pkg)
                addView(
                    ModernUi.actionRow(
                        this@DeviceLifecycleActivity,
                        ModernUi.action(this@DeviceLifecycleActivity, "Block uninstall") { show(gateway.setUninstallBlockedPolicy(pkg.text.toString(), true).toString()) },
                        ModernUi.action(this@DeviceLifecycleActivity, "Allow uninstall") { show(gateway.setUninstallBlockedPolicy(pkg.text.toString(), false).toString()) },
                    )
                )
                addView(restrictions)
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Apply application restrictions") {
                        val parsed = parseKeyValues(restrictions.text.toString())
                        if (parsed == null) show("Invalid restrictions. Use key=value;key2=value2")
                        else show(gateway.setManagedApplicationRestrictions(pkg.text.toString(), parsed).toString())
                    }
                )
                addView(ModernUi.spacer(this@DeviceLifecycleActivity, 7))
                addView(userControl)
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Apply user-control-disabled packages") {
                        show(gateway.setUserControlDisabledPackagesPolicy(csv(userControl.text.toString())).toString())
                    }
                )
                addView(ModernUi.spacer(this@DeviceLifecycleActivity, 7))
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Confirm Clear app data", destructive = true) {
                        confirm("Confirm clear data", "This removes application data for ${pkg.text}") {
                            show(gateway.clearManagedApplicationData(pkg.text.toString()).toString())
                        }
                    }
                )
            }
        )

        root.addView(ModernUi.section(this, "Factory Reset Protection"))
        val accounts = ModernUi.field(this, "FRP account IDs, comma-separated")
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@DeviceLifecycleActivity, "These actions change FRP policy only; they do not trigger a factory reset."))
                addView(accounts)
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Preview / Confirm FRP", primary = true) {
                        val spec = FrpPolicySpec(true, csv(accounts.text.toString()).toList())
                        confirm(
                            "Preview Factory Reset Protection",
                            "Current: ${gateway.getFrpPolicySpec()}\nRequested: $spec\nNo factory reset is triggered by this action.",
                        ) {
                            val apply = gateway.setFrpPolicySpec(spec)
                            val readback = gateway.getFrpPolicySpec()
                            show("Apply=${apply.status}\nReadback=$readback")
                        }
                    }
                )
                addView(ModernUi.spacer(this@DeviceLifecycleActivity, 7))
                addView(
                    ModernUi.action(this@DeviceLifecycleActivity, "Clear custom FRP policy", destructive = true) {
                        confirm("Clear custom FRP policy", "This changes only the FRP policy; it does not factory-reset the device.") {
                            show(gateway.setFrpPolicySpec(FrpPolicySpec(enabled = true, accountIds = emptyList())).toString())
                        }
                    }
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, root))
    }

    private fun csv(value: String): Set<String> = value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun parseKeyValues(value: String): Map<String, String>? {
        if (value.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (part in value.split(';')) {
            val index = part.indexOf('=')
            if (index <= 0) return null
            val key = part.substring(0, index).trim()
            val item = part.substring(index + 1).trim()
            if (key.isEmpty()) return null
            out[key] = item
        }
        return out
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ -> action() }.show()
    }

    private fun show(msg: String) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()
    }
}
