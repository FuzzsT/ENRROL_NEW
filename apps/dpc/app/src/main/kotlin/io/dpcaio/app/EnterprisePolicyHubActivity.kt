package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import io.dpcaio.core.model.CapabilityResolution
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.policy.AppFunctionsPolicy
import io.dpcaio.policy.DeviceRestriction
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.TriStatePolicy
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class EnterprisePolicyHubActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var gateway: AndroidDevicePolicyGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Enterprise Policy Hub"
        gateway = AndroidDevicePolicyGateway(this, ComponentName(this, AioDeviceAdminReceiver::class.java))
        render()
    }

    private fun render() {
        val management = ManagementContextFactory.create(this)
        root = LinearLayout(this)
        val ownership = management.ownership.toString()
        val profileSuffix = if (management.organizationOwnedProfile) " • organization-owned profile" else ""

        root.addView(ModernUi.hero(
            this,
            title = "Enterprise Policy Hub",
            subtitle = "Android API ${management.apiLevel} • $ownership$profileSuffix",
            eyebrow = "Device policy",
        ))

        val summary = ModernUi.card(this, emphasized = true).apply {
            addView(ModernUi.title(this@EnterprisePolicyHubActivity, "Capability-aware controls"))
            addView(ModernUi.body(
                this@EnterprisePolicyHubActivity,
                "Only controls visible for this API level and management context are shown. High-impact actions still require confirmation.",
            ))
            addView(ModernUi.chip(this@EnterprisePolicyHubActivity, ownership, "success"))
        }
        root.addView(summary)

        EnterprisePolicyGroup.entries.forEach { group ->
            val entries = EnterprisePolicyCatalog.entries
                .map { it to CapabilityResolver.resolve(it.requirements, management) }
                .filter { (entry, resolution) -> entry.group == group && resolution.visible }
            if (entries.isEmpty()) return@forEach

            root.addView(ModernUi.section(this, group.label))
            entries.forEach { (entry, resolution) -> addEntry(entry, resolution) }
        }
        setContentView(ModernUi.page(this, root))
    }

    private fun addEntry(entry: EnterprisePolicyDescriptor, resolution: CapabilityResolution) {
        val tone = when {
            !resolution.executable -> "warning"
            entry.requirements.risk.name.contains("HIGH", ignoreCase = true) -> "danger"
            else -> "success"
        }
        val box = ModernUi.card(this, emphasized = resolution.executable).apply {
            addView(ModernUi.title(this@EnterprisePolicyHubActivity, entry.title))
            addView(ModernUi.body(
                this@EnterprisePolicyHubActivity,
                "API ${entry.requirements.minApi}+ • ${entry.requirements.ownership} • ${entry.requirements.risk}",
            ))
            addView(ModernUi.chip(this@EnterprisePolicyHubActivity, resolution.availability.name, tone))
            resolution.reason?.let {
                addView(ModernUi.spacer(this@EnterprisePolicyHubActivity, 8))
                addView(ModernUi.body(this@EnterprisePolicyHubActivity, it))
            }
        }

        if (resolution.executable) {
            box.addView(ModernUi.spacer(this, 10))
            when (entry.id) {
                "usb_data" -> addUsbControls(box)
                "auto_time" -> addTriStateControls(box, "Auto time", gateway.getAutoTimePolicy(), gateway::setAutoTimePolicy)
                "auto_timezone" -> addTriStateControls(box, "Auto timezone", gateway.getAutoTimeZonePolicy(), gateway::setAutoTimeZonePolicy)
                "thread_network" -> addRestrictionControls(box, DeviceRestriction.THREAD_NETWORK)
                "nfc_radio" -> addRestrictionControls(box, DeviceRestriction.NFC_RADIO)
                "nfc_changes" -> addRestrictionControls(box, DeviceRestriction.NFC_RADIO_CHANGES)
                "app_functions" -> addAppFunctionsControls(box)
                "local_network_permission" -> addLocalNetworkControls(box)
            }
        }
        root.addView(box)
    }

    private fun addUsbControls(box: LinearLayout) {
        val current = gateway.getUsbDataSignalingEnabled()
        box.addView(ModernUi.body(this, "Current: ${resultValue(current)}"))
        box.addView(ModernUi.spacer(this, 8))
        box.addView(ModernUi.actionRow(
            this,
            ModernUi.action(this, "Enable USB data") { confirmUsb(true) },
            ModernUi.action(this, "Disable USB data", destructive = true) { confirmUsb(false) },
        ))
    }

    private fun confirmUsb(enabled: Boolean) {
        val current = gateway.getUsbDataSignalingEnabled()
        AlertDialog.Builder(this)
            .setTitle("Preview USB data policy")
            .setMessage("Current: ${resultValue(current)}\nRequested: ${if (enabled) "ENABLED" else "DISABLED"}\n\nThis is a device-wide, high-impact policy.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ -> showResult("USB data", gateway.setUsbDataSignalingEnabled(enabled)) }
            .show()
    }

    private fun addTriStateControls(
        box: LinearLayout,
        label: String,
        current: PolicyResult<TriStatePolicy>,
        apply: (TriStatePolicy) -> PolicyResult<Unit>,
    ) {
        box.addView(ModernUi.body(this, "Current: ${resultValue(current)}"))
        box.addView(ModernUi.spacer(this, 8))
        val buttons = TriStatePolicy.entries.map { policy ->
            ModernUi.action(this, policy.name) { showResult(label, apply(policy)) }
        }.toTypedArray()
        box.addView(ModernUi.actionRow(this, *buttons))
    }

    private fun addRestrictionControls(box: LinearLayout, restriction: DeviceRestriction) {
        val current = gateway.getDeviceRestriction(restriction)
        box.addView(ModernUi.body(this, "Restricted: ${resultValue(current)}"))
        box.addView(ModernUi.spacer(this, 8))
        box.addView(ModernUi.actionRow(
            this,
            ModernUi.action(this, "Apply restriction", destructive = true) {
                showResult(restriction.name, gateway.setDeviceRestriction(restriction, true))
            },
            ModernUi.action(this, "Clear restriction") {
                showResult(restriction.name, gateway.setDeviceRestriction(restriction, false))
            },
        ))
    }

    private fun addAppFunctionsControls(box: LinearLayout) {
        val current = gateway.getAppFunctionsPolicy()
        box.addView(ModernUi.body(this, "Current: ${resultValue(current)}"))
        box.addView(ModernUi.spacer(this, 8))
        val buttons = AppFunctionsPolicy.entries.map { policy ->
            ModernUi.action(this, policy.name) { showResult("App Functions", gateway.setAppFunctionsPolicy(policy)) }
        }.toTypedArray()
        if (buttons.size <= 3) {
            box.addView(ModernUi.actionRow(this, *buttons))
        } else {
            buttons.forEach { button ->
                box.addView(button)
                box.addView(ModernUi.spacer(this, 7))
            }
        }
    }

    private fun addLocalNetworkControls(box: LinearLayout) {
        val packageNameInput: EditText = ModernUi.field(this, "Target package (e.g. com.example.app)")
        box.addView(packageNameInput)
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val buttons = listOf(
            "DEFAULT" to ManagedPermissionState.DEFAULT,
            "GRANT" to ManagedPermissionState.GRANTED,
            "DENY" to ManagedPermissionState.DENIED,
        ).map { (label, state) ->
            ModernUi.action(this, label, destructive = state == ManagedPermissionState.DENIED) {
                val pkg = packageNameInput.text.toString().trim()
                if (pkg.isEmpty()) {
                    showMessage("Local network", "Enter a target package name")
                } else {
                    showResult("Local network", gateway.setPermissionGrantState(pkg, permission, state))
                }
            }
        }.toTypedArray()
        box.addView(ModernUi.actionRow(this, *buttons))
    }

    private fun resultValue(result: PolicyResult<*>): String =
        if (result.isSuccess) result.value?.toString() ?: "SUCCESS" else "${result.status}: ${result.message ?: ""}"

    private fun showResult(label: String, result: PolicyResult<*>) {
        showMessage(label, "${result.status}${result.message?.let { ": $it" } ?: ""}")
        render()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
