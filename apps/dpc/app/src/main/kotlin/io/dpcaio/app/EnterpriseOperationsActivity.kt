package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.core.model.RiskClass
import io.dpcaio.policy.SystemUpdateMode
import io.dpcaio.policy.FreezePeriodTextParser
import io.dpcaio.policy.SystemUpdatePolicySpec
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class EnterpriseOperationsActivity : Activity() {
    private lateinit var gateway: AndroidDevicePolicyGateway
    private lateinit var state: EnterpriseLogStateStore
    private lateinit var store: EnterpriseLogStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Enterprise Operations Center"
        gateway = AndroidDevicePolicyGateway(this, ComponentName(this, AioDeviceAdminReceiver::class.java))
        state = EnterpriseLogStateStore(this)
        store = EnterpriseLogStore(this)
        render()
    }

    private fun render() {
        val management = ManagementContextFactory.create(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        root.addView(TextView(this).apply { text = "Compliance & Logs / System Update Policy"; textSize = 20f })

        val logging = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 24, ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE, risk = RiskClass.HIGH),
            management,
        )
        addStatus(root, "Security Logging", logging.availability.name, logging.reason)
        if (logging.executable) {
            addAction(root, "Enable Security Logging") { show("Security Logging", gateway.setSecurityLoggingEnabled(true).toString()) }
            addAction(root, "Disable Security Logging") { show("Security Logging", gateway.setSecurityLoggingEnabled(false).toString()) }
            addAction(root, "Retrieve Security Logs") {
                val result = gateway.retrieveSecurityLogs()
                result.value?.let { store.append(it); state.consumeSecurityAvailability() }
                show("Security Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
            }
            addAction(root, "Pre-reboot logs") {
                val result = gateway.retrievePreRebootSecurityLogs(); result.value?.let(store::append)
                show("Security Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
            }
        }

        val network = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 26, ownership = OwnershipRequirement.DEVICE_OR_PROFILE_OWNER, risk = RiskClass.HIGH),
            management,
        )
        addStatus(root, "Network Logging", network.availability.name, network.reason)
        root.addView(TextView(this).apply { text = "Pending batch token: ${state.networkBatchToken() ?: "none"}" })
        if (network.executable) {
            addAction(root, "Enable Network Logging") { show("Network Logging", gateway.setNetworkLoggingEnabled(true).toString()) }
            addAction(root, "Disable Network Logging") { show("Network Logging", gateway.setNetworkLoggingEnabled(false).toString()) }
            addAction(root, "Retrieve Network Logs") {
                val token = state.networkBatchToken()
                if (token == null) show("Network Logging", "WAITING_FOR_CALLBACK") else {
                    val result = gateway.retrieveNetworkLogs(token)
                    result.value?.let { store.append(it); state.clearNetworkBatch() }
                    show("Network Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
                }
            }
        }

        val updates = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 23, ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE, risk = RiskClass.HIGH),
            management,
        )
        addStatus(root, "System Update Policy", updates.availability.name, updates.reason)
        root.addView(TextView(this).apply { text = "Current: ${gateway.getSystemUpdatePolicySpec()}" })
        if (updates.executable) {
            val freezeText = EditText(this).apply {
                hint = "Freeze periods (MM-DD:MM-DD;...)"
            }
            root.addView(freezeText)
            addUpdateButton(root, "Automatic", SystemUpdatePolicySpec(SystemUpdateMode.AUTOMATIC), freezeText)
            addUpdateButton(root, "Postpone (max 30 days)", SystemUpdatePolicySpec(SystemUpdateMode.POSTPONE), freezeText)
            addUpdateButton(root, "System default", SystemUpdatePolicySpec(SystemUpdateMode.SYSTEM_DEFAULT), freezeText)
            val start = EditText(this).apply { hint = "Window start minute 0..1439"; setText("1380") }
            val end = EditText(this).apply { hint = "Window end minute 0..1439"; setText("120") }
            root.addView(start); root.addView(end)
            addAction(root, "Preview windowed update") {
                previewWithFreeze(
                    SystemUpdatePolicySpec(SystemUpdateMode.WINDOWED, start.text.toString().toIntOrNull(), end.text.toString().toIntOrNull()),
                    freezeText.text.toString(),
                )
            }
        }
        root.addView(TextView(this).apply { text = "Stored log batches: ${store.batches().size}. Manual redacted export only." })
        setContentView(DpcUiShell.scroll(this, root))
    }

    private fun addUpdateButton(root: LinearLayout, label: String, spec: SystemUpdatePolicySpec, freezeText: EditText) =
        addAction(root, "Preview $label") { previewWithFreeze(spec, freezeText.text.toString()) }

    private fun previewWithFreeze(base: SystemUpdatePolicySpec, text: String) {
        if (base.mode == SystemUpdateMode.SYSTEM_DEFAULT) {
            previewUpdate(base)
            return
        }
        val parsed = FreezePeriodTextParser.parse(text)
        if (!parsed.valid) {
            show("Freeze periods", "POLICY_VALIDATION_FAILED: ${parsed.errors.joinToString(",")}")
            return
        }
        previewUpdate(base.copy(freezePeriods = parsed.periods))
    }

    private fun previewUpdate(spec: SystemUpdatePolicySpec) {
        AlertDialog.Builder(this)
            .setTitle("Preview System Update Policy")
            .setMessage("Current: ${gateway.getSystemUpdatePolicySpec()}\nRequested: $spec\n\nApply and read back?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val applied = gateway.setSystemUpdatePolicySpec(spec)
                val readback = gateway.getSystemUpdatePolicySpec()
                show("System Update Policy", "Apply=${applied.status}\nReadback=$readback")
            }.show()
    }

    private fun addStatus(root: LinearLayout, title: String, status: String, reason: String?) {
        root.addView(TextView(this).apply { text = "\n$title\n$status${reason?.let { " • $it" } ?: ""}"; textSize = 17f })
    }
    private fun addAction(root: LinearLayout, label: String, action: () -> Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
    private fun show(title: String, message: String) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show() }
}
