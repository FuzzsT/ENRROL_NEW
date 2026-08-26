package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        host = EditText(this).apply { hint = "Private DNS DoT hostname" }
        endpoint = EditText(this).apply { hint = "DoH endpoint https://..." }
        queryHex = EditText(this).apply { hint = "DNS wire query hex" }
        output = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(host); root.addView(Button(this).apply { text = "Apply Device Owner Private DNS"; setOnClickListener { applyPrivateDns() } })
        root.addView(Button(this).apply { text = "Private DNS opportunistic"; setOnClickListener { opportunistic() } })
        root.addView(endpoint); root.addView(queryHex); root.addView(Button(this).apply { text = "DoH query"; setOnClickListener { doh() } }); root.addView(output)
        setContentView(root)
    }

    private fun controller() = DeviceOwnerPrivateDnsController(this, AioDeviceAdminReceiver.componentName(this))
    private fun applyPrivateDns() { output.text = runCatching { "result=${controller().applySpecifiedHost(host.text.toString().trim())}\nreadback=${controller().readHost()}" }.getOrElse { it.toString() } }
    private fun opportunistic() { output.text = runCatching { "result=${controller().applyOpportunistic()}" }.getOrElse { it.toString() } }
    private fun doh() {
        output.text = "Querying..."
        Thread {
            val result = runCatching { DohDiagnosticClient().query(endpoint.text.toString().trim(), hex(queryHex.text.toString())) }
            runOnUiThread { output.text = result.fold({ it.joinToString("") { b -> "%02X".format(b) } }, { it.toString() }) }
        }.start()
    }
    private fun hex(text: String): ByteArray {
        val clean = text.replace(" ", "").replace(":", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { clean.substring(it*2, it*2+2).toInt(16).toByte() }
    }
}
