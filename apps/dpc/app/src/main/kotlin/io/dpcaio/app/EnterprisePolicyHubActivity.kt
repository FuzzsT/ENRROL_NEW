package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Android API ${management.apiLevel} • ${management.ownership}" +
                if (management.organizationOwnedProfile) " • organization-owned profile" else ""
        })

        EnterprisePolicyGroup.entries.forEach { group ->
            val entries = EnterprisePolicyCatalog.entries.map { it to CapabilityResolver.resolve(it.requirements, management) }
                .filter { (entry, resolution) -> entry.group == group && resolution.visible }
            if (entries.isEmpty()) return@forEach
            root.addView(TextView(this).apply {
                text = "\n${group.label}"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            entries.forEach { (entry, resolution) -> addEntry(entry, resolution) }
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addEntry(entry: EnterprisePolicyDescriptor, resolution: CapabilityResolution) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 14)
        }
        box.addView(TextView(this).apply {
            text = entry.title
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = "${resolution.availability.name} • API ${entry.requirements.minApi}+ • " +
                "${entry.requirements.ownership} • ${entry.requirements.risk}"
        })
        resolution.reason?.let { box.addView(TextView(this).apply { text = it }) }

        if (resolution.executable) {
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
        box.addView(TextView(this).apply { text = "Current: ${resultValue(current)}" })
        box.addView(Button(this).apply {
            text = "Enable USB data"
            setOnClickListener { confirmUsb(true) }
        })
        box.addView(Button(this).apply {
            text = "Disable USB data"
            setOnClickListener { confirmUsb(false) }
        })
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
        box.addView(TextView(this).apply { text = "Current: ${resultValue(current)}" })
        TriStatePolicy.entries.forEach { policy ->
            box.addView(Button(this).apply {
                text = policy.name
                setOnClickListener { showResult(label, apply(policy)) }
            })
        }
    }

    private fun addRestrictionControls(box: LinearLayout, restriction: DeviceRestriction) {
        val current = gateway.getDeviceRestriction(restriction)
        box.addView(TextView(this).apply { text = "Restricted: ${resultValue(current)}" })
        box.addView(Button(this).apply {
            text = "Apply restriction"
            setOnClickListener { showResult(restriction.name, gateway.setDeviceRestriction(restriction, true)) }
        })
        box.addView(Button(this).apply {
            text = "Clear restriction"
            setOnClickListener { showResult(restriction.name, gateway.setDeviceRestriction(restriction, false)) }
        })
    }

    private fun addAppFunctionsControls(box: LinearLayout) {
        val current = gateway.getAppFunctionsPolicy()
        box.addView(TextView(this).apply { text = "Current: ${resultValue(current)}" })
        AppFunctionsPolicy.entries.forEach { policy ->
            box.addView(Button(this).apply {
                text = policy.name
                setOnClickListener { showResult("App Functions", gateway.setAppFunctionsPolicy(policy)) }
            })
        }
    }

    private fun addLocalNetworkControls(box: LinearLayout) {
        val packageNameInput = EditText(this).apply { hint = "Target package (e.g. com.example.app)" }
        box.addView(packageNameInput)
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        listOf(
            "DEFAULT" to ManagedPermissionState.DEFAULT,
            "GRANT" to ManagedPermissionState.GRANTED,
            "DENY" to ManagedPermissionState.DENIED,
        ).forEach { (label, state) ->
            box.addView(Button(this).apply {
                text = "$label ACCESS_LOCAL_NETWORK"
                setOnClickListener {
                    val pkg = packageNameInput.text.toString().trim()
                    if (pkg.isEmpty()) {
                        showMessage("Local network", "Enter a target package name")
                    } else {
                        showResult("Local network", gateway.setPermissionGrantState(pkg, permission, state))
                    }
                }
            })
        }
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
