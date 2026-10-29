package io.dpcaio.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
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
    companion object {
        private val operationLock = Any()
        @Volatile
        private var operationRunning = false
    }

    private lateinit var namespace: Spinner
    private lateinit var key: EditText
    private lateinit var value: EditText
    private lateinit var discoveryFilter: EditText
    private lateinit var discoveredList: TextView
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private var discoveredSettings: List<Pair<String, String?>> = emptyList()
    @Volatile
    private var discoveryGeneration = 0L
    @Volatile
    private var activeStabilityMonitor: AutoCloseable? = null
    private var stabilityCleanup: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = if (Build.MANUFACTURER.equals("samsung", true)) "Samsung Settings Editor" else "Settings Editor"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = title.toString(),
                subtitle = "Read a setting, submit an allowed write route, then verify observed stability",
                eyebrow = "SETTINGS",
            )
        )

        body.addView(ModernUi.section(this, "Setting"))
        body.addView(
            ModernUi.card(this).apply {
                namespace = Spinner(this@SamsungSettingsEditorActivity).apply {
                    adapter = ArrayAdapter(
                        this@SamsungSettingsEditorActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("SYSTEM", "SECURE", "GLOBAL"),
                    )
                }
                key = ModernUi.field(this@SamsungSettingsEditorActivity, "setting key")
                value = ModernUi.field(this@SamsungSettingsEditorActivity, "value")
                addView(namespace)
                addView(key)
                addView(value)
                addView(
                    ModernUi.actionRow(
                        this@SamsungSettingsEditorActivity,
                        ModernUi.action(this@SamsungSettingsEditorActivity, "Read") { readValue() },
                        ModernUi.action(this@SamsungSettingsEditorActivity, "Write + verify stability", primary = true) { writeValue() },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Discovered settings"))
        body.addView(
            ModernUi.card(this).apply {
                discoveryFilter = ModernUi.field(this@SamsungSettingsEditorActivity, "Filter discovered settings")
                discoveryFilter.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderDiscoveredSettings()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(discoveryFilter)
                addView(
                    ModernUi.action(this@SamsungSettingsEditorActivity, "Discover settings") {
                        discoverSettings()
                    }
                )
                discoveredList = TextView(this@SamsungSettingsEditorActivity).apply {
                    text = "No settings discovered yet"
                    setTextIsSelectable(true)
                }
                addView(ModernUi.outputCard(this@SamsungSettingsEditorActivity, discoveredList))
            }
        )

        body.addView(ModernUi.section(this, "Result"))
        status = TextView(this).apply { text = "Ready"; setTextIsSelectable(true) }
        body.addView(ModernUi.outputCard(this, status))

        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@SamsungSettingsEditorActivity, "Verification boundary"))
                addView(
                    ModernUi.body(
                        this@SamsungSettingsEditorActivity,
                        "The editor tries only declared routes and reports before/after plus the verified route. A later observer change is surfaced instead of being hidden.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        shizuku.bind()

        namespace.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                discoverSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        discoverSettings()
    }

    private fun selectedNamespace() = SettingNamespace.valueOf(namespace.selectedItem.toString())

    private fun gateway() = AndroidSamsungSettingGateway(this, shizuku, AndroidUserId.fromUid(Process.myUid()))

    private fun discoverSettings() {
        if (!::discoveredList.isInitialized) return
        val ns = selectedNamespace()
        val generation = ++discoveryGeneration
        discoveredList.text = "Discovering ${ns.name.lowercase()} settings…"
        Thread {
            val rows = gateway().list(ns)
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != discoveryGeneration) return@runOnUiThread
                discoveredSettings = rows
                renderDiscoveredSettings()
                status.text = "Discovered ${rows.size} ${ns.name.lowercase()} settings"
            }
        }.start()
    }

    private fun renderDiscoveredSettings() {
        if (!::discoveredList.isInitialized) return
        val filter = if (::discoveryFilter.isInitialized) discoveryFilter.text.toString().trim().lowercase() else ""
        val rows = discoveredSettings.filter { (name, currentValue) ->
            filter.isBlank() || name.lowercase().contains(filter) || currentValue.orEmpty().lowercase().contains(filter)
        }
        discoveredList.text = if (rows.isEmpty()) {
            "No matching settings"
        } else {
            rows.joinToString("\n") { (name, currentValue) -> "$name = ${currentValue ?: "<null>"}" }
        }
    }

    private fun readValue() {
        val k = key.text.toString().trim(); if (k.isBlank()) return
        status.text = gateway().read(selectedNamespace(), k) ?: "<null/unavailable>"
    }

    private fun cancelStabilityCleanup() {
        if (::status.isInitialized) {
            stabilityCleanup?.let(status::removeCallbacks)
        }
        stabilityCleanup = null
    }

    private fun writeValue() {
        val k = key.text.toString().trim(); if (k.isBlank()) return
        val v = value.text.toString()
        val ns = selectedNamespace()
        val acquired = synchronized(operationLock) {
            if (operationRunning) {
                false
            } else {
                operationRunning = true
                true
            }
        }
        if (!acquired) {
            status.text = "Write already in progress"
            return
        }
        status.text = "Writing..."
        Thread {
            try {
                val routes = listOf(SettingWriteRoute.PUBLIC_SETTINGS, SettingWriteRoute.SHIZUKU_SETTINGS, SettingWriteRoute.SYSTEM_PRIVILEGED)
                val result = SamsungSettingEditCoordinator(gateway(), AndroidThreadSettingDelay).apply(SettingEditRequest(ns, k, v, routes))
                val uri = when (ns) {
                    SettingNamespace.SYSTEM -> Settings.System.getUriFor(k)
                    SettingNamespace.SECURE -> Settings.Secure.getUriFor(k)
                    SettingNamespace.GLOBAL -> Settings.Global.getUriFor(k)
                    else -> null
                }
                val monitor = uri?.let {
                    AndroidSettingStabilityMonitor(contentResolver).observe(it) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            status.append("\nChanged again after write")
                        }
                    }
                }
                synchronized(this) {
                    activeStabilityMonitor?.close()
                    activeStabilityMonitor = monitor
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        monitor?.close()
                        synchronized(this) {
                            if (activeStabilityMonitor === monitor) activeStabilityMonitor = null
                        }
                        return@runOnUiThread
                    }
                    status.text = "manufacturer=${Build.MANUFACTURER}\nstatus=${result.status}\nroute=${result.verifiedRoute}\nbefore=${result.before}\nafter=${result.after}\nattempts=${result.attempts}"
                    cancelStabilityCleanup()
                    lateinit var cleanup: Runnable
                    cleanup = Runnable {
                        monitor?.close()
                        synchronized(this) {
                            if (activeStabilityMonitor === monitor) activeStabilityMonitor = null
                        }
                        if (stabilityCleanup === cleanup) stabilityCleanup = null
                    }
                    stabilityCleanup = cleanup
                    status.postDelayed(cleanup, 5000)
                }
            } finally {
                synchronized(operationLock) {
                    operationRunning = false
                }
            }
        }.start()
    }

    override fun onDestroy() {
        discoveryGeneration++
        cancelStabilityCleanup()
        synchronized(this) {
            activeStabilityMonitor?.close()
            activeStabilityMonitor = null
        }
        super.onDestroy()
    }
}
