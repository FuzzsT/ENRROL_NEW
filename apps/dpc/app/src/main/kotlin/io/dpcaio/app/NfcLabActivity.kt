package io.dpcaio.app

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        output = TextView(this).apply { setTextIsSelectable(true) }
        trace = EditText(this).apply { hint = "Paste DPC-AIO-NFC/1 trace for owned/synthetic HCE replay"; minLines = 8 }
        root.addView(Button(this).apply { text = "Enable reader"; setOnClickListener { enableReader() } })
        root.addView(trace)
        root.addView(Button(this).apply { text = "Install replay trace"; setOnClickListener { installTrace() } })
        root.addView(Button(this).apply { text = "Clear replay"; setOnClickListener { SyntheticNfcReplayRegistry.clear(); output.text = "replay cleared" } })
        root.addView(output)
        DpcUiShell.install(this, root)
        setContentView(root)
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
