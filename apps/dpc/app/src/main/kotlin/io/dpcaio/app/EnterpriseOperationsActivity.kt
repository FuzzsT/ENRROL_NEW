package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.core.model.RiskClass
import io.dpcaio.policy.FreezePeriodTextParser
import io.dpcaio.policy.SystemUpdateMode
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
        val root = ModernUi.page(this)

        root.addView(
            ModernUi.hero(
                this,
                title = "Enterprise Operations",
                subtitle = "Compliance logs and controlled system update policy",
                eyebrow = "OPERATIONS CENTER",
            )
        )

        val logging = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 24, ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE, risk = RiskClass.HIGH),
            management,
        )
        root.addView(ModernUi.section(this, "Security Logging"))
        root.addView(
            capabilityCard("Security Logging", logging.availability.name, logging.reason).apply {
                if (logging.executable) {
                    addView(
                        ModernUi.actionRow(
                            this@EnterpriseOperationsActivity,
                            ModernUi.action(this@EnterpriseOperationsActivity, "Enable") { show("Security Logging", gateway.setSecurityLoggingEnabled(true).toString()) },
                            ModernUi.action(this@EnterpriseOperationsActivity, "Disable", destructive = true) { show("Security Logging", gateway.setSecurityLoggingEnabled(false).toString()) },
                        )
                    )
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Retrieve Security Logs", primary = true) {
                        val result = gateway.retrieveSecurityLogs()
                        result.value?.let { store.append(it); state.consumeSecurityAvailability() }
                        show("Security Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
                    })
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 7))
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Pre-reboot logs") {
                        val result = gateway.retrievePreRebootSecurityLogs()
                        result.value?.let(store::append)
                        show("Security Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
                    })
                }
            }
        )

        val network = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 26, ownership = OwnershipRequirement.DEVICE_OR_PROFILE_OWNER, risk = RiskClass.HIGH),
            management,
        )
        root.addView(ModernUi.section(this, "Network Logging"))
        root.addView(
            capabilityCard("Network Logging", network.availability.name, network.reason).apply {
                addView(ModernUi.body(this@EnterpriseOperationsActivity, "Pending batch token: ${state.networkBatchToken() ?: "none"}"))
                if (network.executable) {
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 8))
                    addView(
                        ModernUi.actionRow(
                            this@EnterpriseOperationsActivity,
                            ModernUi.action(this@EnterpriseOperationsActivity, "Enable") { show("Network Logging", gateway.setNetworkLoggingEnabled(true).toString()) },
                            ModernUi.action(this@EnterpriseOperationsActivity, "Disable", destructive = true) { show("Network Logging", gateway.setNetworkLoggingEnabled(false).toString()) },
                        )
                    )
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Retrieve Network Logs", primary = true) {
                        val token = state.networkBatchToken()
                        if (token == null) {
                            show("Network Logging", "WAITING_FOR_CALLBACK")
                        } else {
                            val result = gateway.retrieveNetworkLogs(token)
                            result.value?.let { store.append(it); state.clearNetworkBatch() }
                            show("Network Logging", "${result.status}; events=${result.value?.eventCount ?: 0}")
                        }
                    })
                }
            }
        )

        val updates = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 23, ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE, risk = RiskClass.HIGH),
            management,
        )
        root.addView(ModernUi.section(this, "System Update Policy"))
        val freezeText = ModernUi.field(this, "Freeze periods (MM-DD:MM-DD;...)")
        val start = ModernUi.field(this, "Window start minute 0..1439").apply { setText("1380") }
        val end = ModernUi.field(this, "Window end minute 0..1439").apply { setText("120") }
        root.addView(
            capabilityCard("System Update Policy", updates.availability.name, updates.reason).apply {
                addView(ModernUi.body(this@EnterpriseOperationsActivity, "Current: ${gateway.getSystemUpdatePolicySpec()}"))
                if (updates.executable) {
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 8))
                    addView(freezeText)
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Preview Automatic") {
                        previewWithFreeze(SystemUpdatePolicySpec(SystemUpdateMode.AUTOMATIC), freezeText.text.toString())
                    })
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 7))
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Preview Postpone (max 30 days)") {
                        previewWithFreeze(SystemUpdatePolicySpec(SystemUpdateMode.POSTPONE), freezeText.text.toString())
                    })
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 7))
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Preview System default") {
                        previewWithFreeze(SystemUpdatePolicySpec(SystemUpdateMode.SYSTEM_DEFAULT), freezeText.text.toString())
                    })
                    addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 10))
                    addView(start)
                    addView(end)
                    addView(ModernUi.action(this@EnterpriseOperationsActivity, "Preview windowed update", primary = true) {
                        previewWithFreeze(
                            SystemUpdatePolicySpec(SystemUpdateMode.WINDOWED, start.text.toString().toIntOrNull(), end.text.toString().toIntOrNull()),
                            freezeText.text.toString(),
                        )
                    })
                }
            }
        )

        root.addView(ModernUi.section(this, "Stored logs"))
        root.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@EnterpriseOperationsActivity, "Stored log batches"))
                addView(ModernUi.body(this@EnterpriseOperationsActivity, "Stored log batches: ${store.batches().size}. Manual redacted export only."))
                addView(ModernUi.chip(this@EnterpriseOperationsActivity, "REDACTED EXPORT ONLY", ModernUi.Tone.WARNING))
            }
        )

        setContentView(ModernUi.scrollPage(this, root))
    }

    private fun capabilityCard(title: String, status: String, reason: String?): LinearLayout =
        ModernUi.card(this).apply {
            addView(ModernUi.title(this@EnterpriseOperationsActivity, title))
            addView(ModernUi.chip(this@EnterpriseOperationsActivity, status, if (status.contains("AVAILABLE")) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING))
            reason?.let {
                addView(ModernUi.spacer(this@EnterpriseOperationsActivity, 7))
                addView(ModernUi.body(this@EnterpriseOperationsActivity, it))
            }
        }

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

    private fun show(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
