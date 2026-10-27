package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.network.android.DeviceOwnerPrivateDnsController
import io.dpcaio.network.android.DohDiagnosticClient

class NetworkControlActivity : Activity() {
    private lateinit var host: EditText
    private lateinit var endpoint: EditText
    private lateinit var queryHex: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Network / DNS / DoH"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Network control",
                subtitle = "Device Owner Private DNS and DNS-over-HTTPS diagnostics",
                eyebrow = "NETWORK / DNS / DoH",
            )
        )

        body.addView(ModernUi.section(this, "Private DNS"))
        val privateDnsCard = ModernUi.card(this)
        host = ModernUi.field(this, "Private DNS DoT hostname")
        privateDnsCard.addView(host)
        privateDnsCard.addView(
            ModernUi.action(this, "Apply Device Owner Private DNS", primary = true) { applyPrivateDns() }
        )
        privateDnsCard.addView(
            ModernUi.action(this, "Use opportunistic mode") { opportunistic() }
        )
        body.addView(privateDnsCard)

        body.addView(ModernUi.section(this, "DNS over HTTPS"))
        val dohCard = ModernUi.card(this)
        endpoint = ModernUi.field(this, "DoH endpoint https://...")
        queryHex = ModernUi.field(this, "DNS wire query hex")
        dohCard.addView(endpoint)
        dohCard.addView(queryHex)
        dohCard.addView(ModernUi.action(this, "Run DoH query", primary = true) { doh() })
        body.addView(dohCard)

        body.addView(ModernUi.section(this, "Result"))
        output = ModernUi.outputCard(this, "Ready")
        body.addView(output)

        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun controller() = DeviceOwnerPrivateDnsController(this, AioDeviceAdminReceiver.componentName(this))

    private fun applyPrivateDns() {
        output.text = runCatching {
            "Private DNS\nresult=${controller().applySpecifiedHost(host.text.toString().trim())}\nreadback=${controller().readHost()}"
        }.getOrElse { it.toString() }
    }

    private fun opportunistic() {
        output.text = runCatching {
            "Private DNS opportunistic\nresult=${controller().applyOpportunistic()}"
        }.getOrElse { it.toString() }
    }

    private fun doh() {
        output.text = "Querying DNS over HTTPS..."
        Thread {
            val result = runCatching {
                DohDiagnosticClient().query(endpoint.text.toString().trim(), hex(queryHex.text.toString()))
            }
            runOnUiThread {
                output.text = result.fold(
                    { bytes -> "DoH response\n${bytes.joinToString("") { b -> "%02X".format(b) }}" },
                    { error -> "DoH error\n$error" },
                )
            }
        }.start()
    }

    private fun hex(text: String): ByteArray {
        val clean = text.replace(" ", "").replace(":", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
