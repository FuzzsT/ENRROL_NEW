package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }

        section(root, "Kiosk / Lock Task")
        val kiosk = EditText(this).apply { hint = "Comma-separated allowed packages" }
        root.addView(kiosk)
        val systemInfo = feature(root, "System info")
        val notifications = feature(root, "Notifications")
        val home = feature(root, "Home")
        val overview = feature(root, "Overview")
        val globalActions = feature(root, "Global actions")
        val keyguard = feature(root, "Keyguard")
        root.addView(Button(this).apply {
            text = "Preview Lock Task"
            setOnClickListener {
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
        })

        section(root, "Single-use / System UI")
        root.addView(Button(this).apply {
            text = "Disable status bar"
            setOnClickListener {
                confirm("Disable status bar", "Hide status/navigation access where DevicePolicyManager allows it?") {
                    show(gateway.setStatusBarDisabledPolicy(true).toString())
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Enable status bar"
            setOnClickListener { show(gateway.setStatusBarDisabledPolicy(false).toString()) }
        })
        root.addView(Button(this).apply {
            text = "Disable keyguard"
            setOnClickListener {
                confirm("Disable keyguard", "This works only for supported owner/affiliation states and normally fails when a device credential is set.") {
                    show(gateway.setKeyguardDisabledPolicy(true).toString())
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Enable keyguard"
            setOnClickListener { show(gateway.setKeyguardDisabledPolicy(false).toString()) }
        })
        root.addView(Button(this).apply {
            text = "Lock device now"
            setOnClickListener {
                confirm("Lock device now", "Immediately request a strong device lock using the official DevicePolicyManager API?") {
                    show(gateway.lockDeviceNow().toString())
                }
            }
        })

        section(root, "Device Security")
        root.addView(TextView(this).apply { text = "Password complexity" })
        val wipe = EditText(this).apply { hint = "Failed-password wipe threshold (0 disables)"; setText("0") }
        root.addView(wipe)
        val disableCamera = CheckBox(this).apply { text = "Disable camera" }
        val disableScreenCapture = CheckBox(this).apply { text = "Disable screen capture" }
        val disableBiometrics = CheckBox(this).apply { text = "Disable keyguard biometrics" }
        root.addView(disableCamera); root.addView(disableScreenCapture); root.addView(disableBiometrics)
        root.addView(Button(this).apply {
            text = "Preview / Confirm security policy"
            setOnClickListener {
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
        })

        section(root, "Application Control")
        val pkg = EditText(this).apply { hint = "Target package" }
        root.addView(pkg)
        root.addView(Button(this).apply { text = "Block uninstall"; setOnClickListener { show(gateway.setUninstallBlockedPolicy(pkg.text.toString(), true).toString()) } })
        root.addView(Button(this).apply { text = "Allow uninstall"; setOnClickListener { show(gateway.setUninstallBlockedPolicy(pkg.text.toString(), false).toString()) } })
        root.addView(Button(this).apply { text = "Hide app"; setOnClickListener { show(gateway.setApplicationHidden(pkg.text.toString().trim(), true).toString()) } })
        root.addView(Button(this).apply { text = "Unhide app"; setOnClickListener { show(gateway.setApplicationHidden(pkg.text.toString().trim(), false).toString()) } })
        root.addView(Button(this).apply { text = "Read hidden state"; setOnClickListener { show(gateway.isApplicationHidden(pkg.text.toString().trim()).toString()) } })
        root.addView(Button(this).apply { text = "Suspend app"; setOnClickListener { show(gateway.setPackagesSuspended(setOf(pkg.text.toString().trim()), true).toString()) } })
        root.addView(Button(this).apply { text = "Unsuspend app"; setOnClickListener { show(gateway.setPackagesSuspended(setOf(pkg.text.toString().trim()), false).toString()) } })
        root.addView(Button(this).apply { text = "Read suspended state"; setOnClickListener { show(gateway.isPackageSuspended(pkg.text.toString().trim()).toString()) } })
        root.addView(Button(this).apply { text = "Enable system app"; setOnClickListener { show(gateway.enableSystemAppPolicy(pkg.text.toString().trim()).toString()) } })

        val delegatedScopes = EditText(this).apply { hint = "Delegated scopes, comma-separated" }
        root.addView(delegatedScopes)
        root.addView(Button(this).apply {
            text = "Read delegated scopes"
            setOnClickListener { show(gateway.getDelegatedScopes(pkg.text.toString().trim()).toString()) }
        })
        root.addView(Button(this).apply {
            text = "Apply delegated scopes"
            setOnClickListener { show(gateway.setDelegatedScopes(pkg.text.toString().trim(), csv(delegatedScopes.text.toString())).toString()) }
        })
        val restrictions = EditText(this).apply { hint = "Restrictions: key=value;key2=value2" }
        root.addView(restrictions)
        root.addView(Button(this).apply {
            text = "Apply application restrictions"
            setOnClickListener {
                val parsed = parseKeyValues(restrictions.text.toString())
                if (parsed == null) show("Invalid restrictions. Use key=value;key2=value2")
                else show(gateway.setManagedApplicationRestrictions(pkg.text.toString(), parsed).toString())
            }
        })
        val userControl = EditText(this).apply { hint = "User-control-disabled packages, comma-separated" }
        root.addView(userControl)
        root.addView(Button(this).apply {
            text = "Apply user-control-disabled packages"
            setOnClickListener { show(gateway.setUserControlDisabledPackagesPolicy(csv(userControl.text.toString())).toString()) }
        })
        root.addView(Button(this).apply {
            text = "Confirm Clear app data"
            setOnClickListener {
                confirm("Confirm clear data", "This removes application data for ${pkg.text}") {
                    show(gateway.clearManagedApplicationData(pkg.text.toString()).toString())
                }
            }
        })

        section(root, "Factory Reset Protection")
        val accounts = EditText(this).apply { hint = "FRP account IDs, comma-separated" }
        root.addView(accounts)
        root.addView(Button(this).apply {
            text = "Preview / Confirm FRP"
            setOnClickListener {
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
        })
        root.addView(Button(this).apply {
            text = "Clear custom FRP policy"
            setOnClickListener {
                confirm("Clear custom FRP policy", "This changes only the FRP policy; it does not factory-reset the device.") {
                    show(gateway.setFrpPolicySpec(FrpPolicySpec(enabled = true, accountIds = emptyList())).toString())
                }
            }
        })

        setContentView(DpcUiShell.scroll(this, root))
    }

    private fun feature(root: LinearLayout, label: String): CheckBox =
        CheckBox(this).apply { text = label; root.addView(this) }

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

    private fun section(root: LinearLayout, title: String) { root.addView(TextView(this).apply { text = "\n$title"; textSize = 18f }) }
    private fun confirm(title: String, msg: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ -> action() }.show() }
    private fun show(msg: String) { AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show() }
}
