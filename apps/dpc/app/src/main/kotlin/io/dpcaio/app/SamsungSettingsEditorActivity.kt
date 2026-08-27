package io.dpcaio.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.samsung.settings.SamsungSettingEditCoordinator
import io.dpcaio.samsung.settings.SettingEditRequest
import io.dpcaio.samsung.settings.SettingNamespace
import io.dpcaio.samsung.settings.SettingWriteRoute
import io.dpcaio.samsung.settings.android.AndroidSamsungSettingGateway
import io.dpcaio.samsung.settings.android.AndroidSettingStabilityMonitor
import io.dpcaio.samsung.settings.android.AndroidThreadSettingDelay
import io.dpcaio.shizuku.ShizukuUserServiceClient

class SamsungSettingsEditorActivity : Activity() {
    private lateinit var namespace: Spinner
    private lateinit var key: EditText
    private lateinit var value: EditText
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = if (Build.MANUFACTURER.equals("samsung", true)) "Samsung Settings Editor" else "Settings Editor"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingDp(20,20,20,20) }
        namespace = Spinner(this).apply { adapter = ArrayAdapter(this@SamsungSettingsEditorActivity, android.R.layout.simple_spinner_dropdown_item, listOf("SYSTEM","SECURE","GLOBAL")) }
        key = EditText(this).apply { hint = "setting key" }
        value = EditText(this).apply { hint = "value" }
        status = TextView(this).apply { setTextIsSelectable(true) }
        val read = Button(this).apply { text = "Read"; setOnClickListener { readValue() } }
        val write = Button(this).apply { text = "Write + verify stability"; setOnClickListener { writeValue() } }
        root.addView(namespace); root.addView(key); root.addView(value); root.addView(read); root.addView(write)
        root.addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        DpcUiShell.install(this, root)
        setContentView(root)
        shizuku.bind()
    }

    private fun selectedNamespace() = SettingNamespace.valueOf(namespace.selectedItem.toString())

    private fun gateway() = AndroidSamsungSettingGateway(this, shizuku, AndroidUserId.fromUid(Process.myUid()))

    private fun readValue() {
        val k = key.text.toString().trim(); if (k.isBlank()) return
        status.text = gateway().read(selectedNamespace(), k) ?: "<null/unavailable>"
    }

    private fun writeValue() {
        val k = key.text.toString().trim(); if (k.isBlank()) return
        val v = value.text.toString()
        status.text = "Writing..."
        Thread {
            val ns = selectedNamespace()
            val routes = listOf(SettingWriteRoute.PUBLIC_SETTINGS, SettingWriteRoute.SHIZUKU_SETTINGS, SettingWriteRoute.SYSTEM_PRIVILEGED)
            val result = SamsungSettingEditCoordinator(gateway(), AndroidThreadSettingDelay).apply(SettingEditRequest(ns, k, v, routes))
            val uri = when (ns) {
                SettingNamespace.SYSTEM -> Settings.System.getUriFor(k)
                SettingNamespace.SECURE -> Settings.Secure.getUriFor(k)
                SettingNamespace.GLOBAL -> Settings.Global.getUriFor(k)
                else -> null
            }
            val monitor = uri?.let { AndroidSettingStabilityMonitor(contentResolver).observe(it) { runOnUiThread { status.append("\nChanged again after write") } } }
            runOnUiThread {
                status.text = "manufacturer=${Build.MANUFACTURER}\nstatus=${result.status}\nroute=${result.verifiedRoute}\nbefore=${result.before}\nafter=${result.after}\nattempts=${result.attempts}"
                status.postDelayed({ monitor?.close() }, 5000)
            }
        }.start()
    }
}
