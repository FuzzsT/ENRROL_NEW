package io.dpcaio.app

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.nfc.NfcTraceCodec
import io.dpcaio.nfc.android.NfcTagInspector
import io.dpcaio.nfc.android.SyntheticNfcReplayRegistry

class NfcLabActivity : Activity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private lateinit var output: TextView
    private lateinit var trace: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "NFC Lab"
        adapter = NfcAdapter.getDefaultAdapter(this)

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "NFC Lab",
                subtitle = "Reader diagnostics and owned/synthetic trace replay",
                eyebrow = "DEVELOPER LAB",
            )
        )

        body.addView(ModernUi.section(this, "Reader"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@NfcLabActivity, "Reads NFC-A/B/F/V and barcode tags using Android reader mode."))
                addView(ModernUi.action(this@NfcLabActivity, "Enable reader", primary = true) { enableReader() })
            }
        )

        body.addView(ModernUi.section(this, "Synthetic / owned replay"))
        body.addView(
            ModernUi.card(this).apply {
                trace = ModernUi.field(this@NfcLabActivity, "Paste DPC-AIO-NFC/1 trace for owned/synthetic HCE replay").apply {
                    setSingleLine(false)
                    minLines = 8
                    gravity = Gravity.TOP or Gravity.START
                }
                addView(trace)
                addView(
                    ModernUi.actionRow(
                        this@NfcLabActivity,
                        ModernUi.action(this@NfcLabActivity, "Install replay trace") { installTrace() },
                        ModernUi.action(this@NfcLabActivity, "Clear replay", destructive = true) {
                            SyntheticNfcReplayRegistry.clear()
                            output.text = "replay cleared"
                        },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Result"))
        output = TextView(this).apply { text = if (adapter == null) "NFC unavailable" else "Ready"; setTextIsSelectable(true) }
        body.addView(ModernUi.outputCard(this, output))
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun enableReader() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE
        adapter?.enableReaderMode(this, this, flags, null)
        output.text = if (adapter == null) "NFC unavailable" else "reader enabled"
    }

    private fun installTrace() {
        runCatching { NfcTraceCodec.decode(trace.text.toString()) }
            .onSuccess { SyntheticNfcReplayRegistry.install(it); output.text = "synthetic/owned trace installed: ${it.technology}" }
            .onFailure { output.text = "trace rejected: ${it.message}" }
    }

    override fun onTagDiscovered(tag: android.nfc.Tag) {
        val snap = NfcTagInspector().inspect(tag)
        runOnUiThread { output.text = "id=${snap.idHex}\ntech=${snap.techList.joinToString()}\nMIFARE Classic=${snap.mifareClassic}\nMIFARE Ultralight=${snap.mifareUltralight}\nIsoDep=${snap.isoDep}\nNDEF=${snap.ndef}" }
    }

    override fun onPause() { adapter?.disableReaderMode(this); super.onPause() }
}
