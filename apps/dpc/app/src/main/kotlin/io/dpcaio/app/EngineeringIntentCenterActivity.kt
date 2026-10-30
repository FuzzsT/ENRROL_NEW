package io.dpcaio.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.app.engineering.BroadcastExtra
import io.dpcaio.app.engineering.BroadcastIntentBuilder
import io.dpcaio.app.engineering.BroadcastRequest
import io.dpcaio.app.engineering.SamsungSecretCodeCatalog
import io.dpcaio.app.engineering.SamsungSecretCodeDiscovery
import io.dpcaio.app.engineering.UssdIntentFactory

class EngineeringIntentCenterActivity : Activity() {
    private lateinit var broadcastAction: EditText
    private lateinit var broadcastPackage: EditText
    private lateinit var broadcastComponent: EditText
    private lateinit var broadcastData: EditText
    private lateinit var extraKey: EditText
    private lateinit var extraType: EditText
    private lateinit var extraValue: EditText
    private lateinit var dialCode: EditText
    private lateinit var secretCode: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Engineering Intent Center"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Engineering Intent Center",
                subtitle = "Bounded Android intent diagnostics: broadcasts, dial/USSD, Samsung receiver discovery and developer activities.",
                eyebrow = "INTENTS • TELEPHONY • COMPONENTS",
            )
        )

        body.addView(ModernUi.section(this, "Broadcast Lab"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                broadcastAction = ModernUi.field(this@EngineeringIntentCenterActivity, "Action (required)")
                broadcastPackage = ModernUi.field(this@EngineeringIntentCenterActivity, "Target package (optional)")
                broadcastComponent = ModernUi.field(this@EngineeringIntentCenterActivity, "Target component pkg/.Receiver (optional)")
                broadcastData = ModernUi.field(this@EngineeringIntentCenterActivity, "Data URI (optional)")
                extraKey = ModernUi.field(this@EngineeringIntentCenterActivity, "Extra key (optional)")
                extraType = ModernUi.field(this@EngineeringIntentCenterActivity, "Extra type: string / int / bool / uri").apply {
                    setText("string")
                }
                extraValue = ModernUi.field(this@EngineeringIntentCenterActivity, "Extra value")
                addView(broadcastAction)
                addView(broadcastPackage)
                addView(broadcastComponent)
                addView(broadcastData)
                addView(extraKey)
                addView(extraType)
                addView(extraValue)
                addView(ModernUi.spacer(this@EngineeringIntentCenterActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@EngineeringIntentCenterActivity,
                        ModernUi.action(this@EngineeringIntentCenterActivity, "Preview broadcast") { previewBroadcast() },
                        ModernUi.action(this@EngineeringIntentCenterActivity, "Send broadcast", primary = true) { sendBoundedBroadcast() },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "USSD / Dial Lab"))
        body.addView(
            ModernUi.card(this).apply {
                dialCode = ModernUi.field(this@EngineeringIntentCenterActivity, "Number or USSD string")
                addView(dialCode)
                addView(ModernUi.spacer(this@EngineeringIntentCenterActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@EngineeringIntentCenterActivity,
                        ModernUi.action(this@EngineeringIntentCenterActivity, "Preview dial") { previewDial() },
                        ModernUi.action(this@EngineeringIntentCenterActivity, "Open dialer", primary = true) { openDialer() },
                    )
                )
                addView(
                    ModernUi.action(this@EngineeringIntentCenterActivity, "Direct call (only with CALL_PHONE)") {
                        directCallIfGranted()
                    }
                )
            }
        )

        body.addView(ModernUi.section(this, "Samsung Secret Code Center"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@EngineeringIntentCenterActivity,
                        "Receiver discovery only for user-supplied codes. Unknown or risky codes are not auto-executed; protected Samsung/system receivers remain permission-gated.",
                    )
                )
                secretCode = ModernUi.field(this@EngineeringIntentCenterActivity, "Secret code or inner code")
                addView(secretCode)
                addView(ModernUi.spacer(this@EngineeringIntentCenterActivity, 8))
                addView(ModernUi.action(this@EngineeringIntentCenterActivity, "Scan secret-code receivers") { scanSecretCodeReceivers() })
                addView(ModernUi.action(this@EngineeringIntentCenterActivity, "Open calculator") { openCalculator() })
            }
        )

        body.addView(ModernUi.section(this, "Hidden Developer Activities"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@EngineeringIntentCenterActivity,
                        "Developer/debug/engineering activities are classified from component metadata. Existing exported/permission/route guards still decide whether an activity can launch.",
                    )
                )
                addView(
                    ModernUi.action(this@EngineeringIntentCenterActivity, "Developer Activities", primary = true) {
                        startActivity(Intent(this@EngineeringIntentCenterActivity, DeveloperActivitiesActivity::class.java))
                    }
                )
            }
        )

        body.addView(ModernUi.section(this, "Latest action"))
        output = TextView(this).apply { text = "Ready." }
        body.addView(ModernUi.outputCard(this, output))

        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun buildBroadcast(): Intent {
        val componentText = broadcastComponent.text.toString().trim()
        val component = componentText.takeIf { it.isNotEmpty() }?.let(ComponentName::unflattenFromString)
        if (componentText.isNotEmpty() && component == null) {
            error("Component must use pkg/.Class or pkg/full.Class format")
        }
        val extras = buildList {
            val key = extraKey.text.toString().trim()
            if (key.isNotEmpty()) {
                val value = extraValue.text.toString()
                when (extraType.text.toString().trim().lowercase()) {
                    "int", "integer" -> add(BroadcastExtra.IntValue(key, value.toInt()))
                    "bool", "boolean" -> add(BroadcastExtra.BooleanValue(key, value.toBooleanStrict()))
                    "uri" -> add(BroadcastExtra.UriValue(key, Uri.parse(value)))
                    else -> add(BroadcastExtra.StringValue(key, value))
                }
            }
        }
        return BroadcastIntentBuilder.build(
            BroadcastRequest(
                action = broadcastAction.text.toString().trim(),
                packageName = broadcastPackage.text.toString().trim().ifEmpty { null },
                component = component,
                data = broadcastData.text.toString().trim().takeIf { it.isNotEmpty() }?.let(Uri::parse),
                extras = extras,
            )
        )
    }

    private fun previewBroadcast() {
        output.text = runCatching {
            val intent = buildBroadcast()
            "BROADCAST PREVIEW\n${intent.toUri(Intent.URI_INTENT_SCHEME)}"
        }.getOrElse { "Broadcast preview failed: ${it.message}" }
    }

    private fun sendBoundedBroadcast() {
        output.text = runCatching {
            val intent = buildBroadcast()
            sendBroadcast(intent)
            "Broadcast submitted through Android Context.sendBroadcast\n${intent.toUri(Intent.URI_INTENT_SCHEME)}"
        }.getOrElse { "Broadcast failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun previewDial() {
        output.text = runCatching {
            val intent = UssdIntentFactory.dial(dialCode.text.toString())
            "DIAL PREVIEW\nACTION_DIAL\n${intent.data}"
        }.getOrElse { "Dial preview failed: ${it.message}" }
    }

    private fun openDialer() {
        output.text = runCatching {
            val intent = UssdIntentFactory.dial(dialCode.text.toString())
            if (intent.resolveActivity(packageManager) == null) error("No dialer handles ACTION_DIAL")
            startActivity(intent)
            "Dialer opened for ${intent.data}"
        }.getOrElse { "Dialer launch failed: ${it.message}" }
    }

    private fun directCallIfGranted() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            output.text = "CALL_PHONE is not granted. Direct call was not started; use ACTION_DIAL instead."
            return
        }
        output.text = runCatching {
            val intent = UssdIntentFactory.call(dialCode.text.toString())
            if (intent.resolveActivity(packageManager) == null) error("No handler for ACTION_CALL")
            startActivity(intent)
            "ACTION_CALL submitted for ${intent.data}"
        }.getOrElse { "Direct call failed: ${it.message}" }
    }

    private fun openCalculator() {
        output.text = runCatching {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            if (intent.resolveActivity(packageManager) == null) error("No calculator handles CATEGORY_APP_CALCULATOR")
            startActivity(intent)
            "Calculator opened through the system app category."
        }.getOrElse { "Calculator launch failed: ${it.message}" }
    }

    private fun scanSecretCodeReceivers() {
        val entry = SamsungSecretCodeCatalog.userSupplied(secretCode.text.toString())
        if (entry.code.isBlank()) {
            output.text = "Enter a secret code to inspect receiver capability."
            return
        }
        val receivers = SamsungSecretCodeDiscovery(this).discover(entry.code)
        output.text = buildString {
            appendLine("SECRET CODE CAPABILITY")
            appendLine("code=${entry.code} risk=${entry.risk} executable=${entry.executable}")
            appendLine("receivers=${receivers.size}")
            receivers.forEach { receiver ->
                appendLine("${receiver.action}")
                appendLine("  ${receiver.packageName}/${receiver.className}")
                appendLine("  enabled=${receiver.enabled} exported=${receiver.exported} permission=${receiver.requiredPermission ?: "none"}")
            }
            if (receivers.isEmpty()) append("No matching receiver is visible to PackageManager on this device.")
        }
    }
}
