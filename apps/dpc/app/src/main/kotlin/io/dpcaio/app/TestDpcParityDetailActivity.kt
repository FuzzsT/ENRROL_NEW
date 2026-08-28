package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.policy.android.parity.AndroidParityRuntimeFactsProvider
import io.dpcaio.policy.parity.ParityActionRequest
import io.dpcaio.policy.parity.ParityAvailability
import io.dpcaio.policy.parity.ParityDestination
import io.dpcaio.policy.parity.ParityInputType
import io.dpcaio.policy.parity.TestDpcCapabilityResolver
import io.dpcaio.policy.parity.TestDpcParityCatalog
import io.dpcaio.policy.parity.TestDpcParityEntry

class TestDpcParityDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TestDPC Parity Detail"
        val parityId = intent.getStringExtra(EXTRA_PARITY_ID)
        val entry = parityId?.let(TestDpcParityCatalog::findById)
        if (entry == null) {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingDp(24, 24, 24, 24)
                addView(TextView(this@TestDpcParityDetailActivity).apply { text = "Unknown TestDPC parity entry." })
            }
            setContentView(DpcUiShell.scroll(this, body))
            return
        }
        render(entry)
    }

    private fun render(entry: TestDpcParityEntry) {
        val facts = AndroidParityRuntimeFactsProvider(
            this,
            ComponentName(this, AioDeviceAdminReceiver::class.java),
        ).read()
        val availability = TestDpcCapabilityResolver.resolve(entry, facts)
        val router = TestDpcParityActionRouter(this)
        val inputViews = linkedMapOf<String, EditText>()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 24, 24, 24)
        }

        body.addView(TextView(this).apply {
            textSize = 20f
            text = displayLabel(entry.googleTitle)
        })
        body.addView(TextView(this).apply {
            text = buildString {
                appendLine(entry.description.ifBlank { displayLabel(entry.googleTitle) })
                appendLine("TestDPC key: ${entry.testDpcKey}")
                appendLine("Google title: ${entry.googleTitle}")
                appendLine("Implementation state: ${entry.implementationState}")
                appendLine("Availability: ${availabilityText(availability)}")
                appendLine("Owner: ${entry.ownerRequirement}")
                appendLine("Minimum API: ${entry.minSdk}")
                appendLine("Features: ${entry.requiredFeatures.ifEmpty { setOf() }.joinToString().ifBlank { "none" }}")
                appendLine("Replacement: ${entry.replacementGuidance ?: "none"}")
                entry.unavailableReason?.let { appendLine("Catalog note: $it") }
                entry.handlerId?.let { appendLine("Handler id: $it") }
                entry.destination?.let { appendLine("Destination: $it") }
                if (entry.destructive) appendLine("Destructive: explicit confirmation required before execution")
            }
        })

        if (entry.inputs.isNotEmpty()) {
            body.addView(TextView(this).apply {
                text = "Inputs"
                textSize = 18f
                setPaddingDp(0, 16, 0, 4)
            })
            entry.inputs.forEach { field ->
                val input = EditText(this).apply {
                    hint = "${field.label}${if (field.required) " *" else ""}"
                    tag = field.key
                    inputType = when (field.type) {
                        ParityInputType.INTEGER, ParityInputType.LONG -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                        ParityInputType.BOOLEAN -> InputType.TYPE_CLASS_TEXT
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                    setSingleLine(field.type != ParityInputType.CSV)
                }
                inputViews[field.key] = input
                body.addView(input)
            }
        }

        entry.destination?.let { destination ->
            destinationActivity(destination)?.let { target ->
                body.addView(Button(this).apply {
                    text = "Open existing DPC-AIO screen"
                    isAllCaps = false
                    setOnClickListener { startActivity(Intent(this@TestDpcParityDetailActivity, target)) }
                })
            }
        }

        val resultView = TextView(this).apply { setPaddingDp(0, 12, 0, 4) }
        val handlerId = entry.handlerId
        if (handlerId != null) {
            if (availability is ParityAvailability.Available && router.isRegistered(handlerId)) {
                body.addView(Button(this).apply {
                    text = "Execute"
                    isAllCaps = false
                    setOnClickListener {
                        val values = inputViews.mapValues { it.value.text?.toString().orEmpty() }
                        val missing = entry.inputs.filter { it.required && values[it.key].isNullOrBlank() }
                        if (missing.isNotEmpty()) {
                            resultView.text = "Missing required input(s): ${missing.joinToString { it.label }}"
                            return@setOnClickListener
                        }
                        val request = ParityActionRequest(parityId = entry.id, values = values)
                        val execute = {
                            val result = router.execute(entry, request)
                            resultView.text = if (result.success) {
                                "Result: ${result.message}"
                            } else {
                                "Action unavailable/failed: ${result.message}"
                            }
                        }
                        if (entry.destructive) {
                            AlertDialog.Builder(this@TestDpcParityDetailActivity)
                                .setTitle("Confirm destructive action")
                                .setMessage(confirmationMessage(entry, values))
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Execute") { _, _ -> execute() }
                                .show()
                        } else {
                            execute()
                        }
                    }
                })
                resultView.text = "Handler registered. Runtime result remains authoritative."
            } else {
                resultView.text = "Execute unavailable: ${availabilityText(availability)}"
            }
            body.addView(resultView)
        }

        setContentView(DpcUiShell.scroll(this, body))
    }

    private fun confirmationMessage(entry: TestDpcParityEntry, values: Map<String, String>): String = buildString {
        appendLine("Google action: ${entry.googleTitle}")
        appendLine("TestDPC key: ${entry.testDpcKey}")
        if (values.isEmpty()) {
            append("Target values: none")
        } else {
            appendLine("Target values:")
            values.toSortedMap().forEach { (key, value) ->
                appendLine("- $key = ${confirmationValue(key, value)}")
            }
        }
    }

    private fun confirmationValue(key: String, value: String): String {
        val normalized = key.lowercase()
        val sensitive = listOf("password", "token", "secret", "credential").any(normalized::contains)
        return if (sensitive && value.isNotEmpty()) "<redacted>" else value.ifBlank { "<empty>" }
    }

    private fun destinationActivity(destination: ParityDestination): Class<out Activity>? = when (destination) {
        ParityDestination.ACTIVITY_MANAGER -> ActivityExplorerActivity::class.java
        ParityDestination.DEVICE_LIFECYCLE -> DeviceLifecycleActivity::class.java
        ParityDestination.ENTERPRISE_POLICY_HUB -> EnterprisePolicyHubActivity::class.java
        ParityDestination.ENTERPRISE_OPERATIONS -> EnterpriseOperationsActivity::class.java
        ParityDestination.CREDENTIAL_CENTER -> CredentialCenterActivity::class.java
        ParityDestination.PERMISSION_MANAGER -> PermissionManagerActivity::class.java
        ParityDestination.NETWORK_CONTROL -> NetworkControlActivity::class.java
        ParityDestination.GOOGLE_ACCOUNT_MANAGER -> GoogleAccountManagerActivity::class.java
        ParityDestination.WORK_PROFILE_COPE -> WorkProfileCopeActivity::class.java
        ParityDestination.TESTDPC_DETAIL -> null
    }

    private fun availabilityText(availability: ParityAvailability): String = when (availability) {
        ParityAvailability.Available -> "Available"
        is ParityAvailability.Unavailable -> "Unsupported: ${availability.reason}"
        is ParityAvailability.Deprecated -> "Deprecated: ${availability.reason}${availability.replacement?.let { "; replacement: $it" }.orEmpty()}"
    }

    private fun displayLabel(raw: String): String {
        val value = raw.removePrefix("@string/").replace('_', ' ')
        return value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    companion object {
        const val EXTRA_PARITY_ID = TestDpcParityCenterActivity.EXTRA_PARITY_ID
    }
}
