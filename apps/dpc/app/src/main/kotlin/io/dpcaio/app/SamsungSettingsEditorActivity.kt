package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    companion object {
        private val operationLock = Any()
        @Volatile
        private var operationRunning = false
    }

    private enum class DiscoveredValueType { INT, STRING }

    private lateinit var namespace: Spinner
    private lateinit var key: EditText
    private lateinit var value: EditText
    private lateinit var discoveryFilter: EditText
    private lateinit var discoveredContainer: LinearLayout
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private var discoveredSettings: List<Pair<String, String?>> = emptyList()
    private val discoveredBaselines = linkedMapOf<String, String?>()
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
                subtitle = "Discover SYSTEM / SECURE / GLOBAL settings, tap a tile, edit its typed value, then verify the write",
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
                    ModernUi.action(this@SamsungSettingsEditorActivity, "Discover / refresh settings") {
                        discoverSettings()
                    }
                )
                discoveredContainer = LinearLayout(this@SamsungSettingsEditorActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ModernUi.body(this@SamsungSettingsEditorActivity, "No settings discovered yet"))
                }
                addView(discoveredContainer)
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
                        "The editor tries only declared routes and reports before/after plus the verified route. The displayed default/baseline is the first value observed during this editor session because Android Settings does not expose a universal factory-default API for every key.",
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
        if (!::discoveredContainer.isInitialized) return
        val ns = selectedNamespace()
        val generation = ++discoveryGeneration
        renderDiscoveryMessage("Discovering ${ns.name.lowercase()} settings…")
        Thread {
            val rows = gateway().list(ns)
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != discoveryGeneration) return@runOnUiThread
                rows.forEach { (name, currentValue) -> rememberBaseline(ns, name, currentValue) }
                discoveredSettings = rows
                renderDiscoveredSettings()
                status.text = "Discovered ${rows.size} ${ns.name.lowercase()} settings"
            }
        }.start()
    }

    private fun rememberBaseline(ns: SettingNamespace, name: String, currentValue: String?) {
        val id = "${ns.name}:$name"
        if (!discoveredBaselines.containsKey(id)) discoveredBaselines[id] = currentValue
    }

    private fun baselineFor(ns: SettingNamespace, name: String): String? = discoveredBaselines["${ns.name}:$name"]

    private fun detectValueType(currentValue: String?): DiscoveredValueType {
        val normalized = currentValue?.trim().orEmpty()
        return if (normalized.isNotEmpty() && normalized.toLongOrNull() != null) {
            DiscoveredValueType.INT
        } else {
            DiscoveredValueType.STRING
        }
    }

    private fun renderDiscoveryMessage(message: String) {
        if (!::discoveredContainer.isInitialized) return
        discoveredContainer.removeAllViews()
        discoveredContainer.addView(ModernUi.body(this, message))
    }

    private fun renderDiscoveredSettings() {
        if (!::discoveredContainer.isInitialized) return
        val filter = if (::discoveryFilter.isInitialized) discoveryFilter.text.toString().trim().lowercase() else ""
        val ns = selectedNamespace()
        val rows = discoveredSettings.filter { (name, currentValue) ->
            filter.isBlank() || name.lowercase().contains(filter) || currentValue.orEmpty().lowercase().contains(filter)
        }

        discoveredContainer.removeAllViews()
        if (rows.isEmpty()) {
            discoveredContainer.addView(ModernUi.body(this, "No matching settings"))
            return
        }

        rows.forEach { (name, currentValue) ->
            val type = detectValueType(currentValue)
            val baseline = baselineFor(ns, name)
            val tile = ModernUi.compactCard(this).apply {
                isClickable = true
                isFocusable = true
                addView(ModernUi.title(this@SamsungSettingsEditorActivity, name))
                addView(
                    ModernUi.body(
                        this@SamsungSettingsEditorActivity,
                        "Current: ${currentValue ?: "<null>"}\nDefault / baseline: ${baseline ?: "<null>"}\nTap to edit",
                    )
                )
                addView(ModernUi.chip(this@SamsungSettingsEditorActivity, type.name))
                setOnClickListener { showDiscoveredSettingEditor(ns, name, currentValue, baseline, type) }
            }
            discoveredContainer.addView(tile)
        }
    }

    private fun showDiscoveredSettingEditor(
        ns: SettingNamespace,
        name: String,
        currentValue: String?,
        baseline: String?,
        type: DiscoveredValueType,
    ) {
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        editor.addView(ModernUi.body(this, "Namespace: ${ns.name}"))
        editor.addView(ModernUi.body(this, "Type: ${type.name}"))
        editor.addView(ModernUi.body(this, "Default / baseline: ${baseline ?: "<null>"}"))

        val input = ModernUi.field(this, "value").apply {
            setText(currentValue.orEmpty())
            setSelection(text.length)
            inputType = when (type) {
                DiscoveredValueType.INT -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                DiscoveredValueType.STRING -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        editor.addView(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(name)
            .setView(editor)
            .setPositiveButton("OK", null)
            .setNegativeButton("Anuluj") { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newValue = input.text.toString()
                if (type == DiscoveredValueType.INT && newValue.trim().toLongOrNull() == null) {
                    input.error = "Expected integer value"
                    return@setOnClickListener
                }

                key.setText(name)
                value.setText(newValue)
                selectNamespace(ns)
                dialog.dismiss()
                writeSetting(ns, name, newValue)
            }
        }
        dialog.show()
    }

    private fun selectNamespace(ns: SettingNamespace) {
        val target = ns.name
        val adapter = namespace.adapter ?: return
        for (index in 0 until adapter.count) {
            if (adapter.getItem(index)?.toString() == target) {
                if (namespace.selectedItemPosition != index) namespace.setSelection(index)
                return
            }
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
        writeSetting(selectedNamespace(), k, value.text.toString())
    }

    private fun writeSetting(ns: SettingNamespace, k: String, v: String) {
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
        status.text = "Writing ${ns.name.lowercase()}/$k..."
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
                    if (selectedNamespace() == ns) {
                        discoveredSettings = discoveredSettings.map { (name, currentValue) ->
                            if (name == k) name to result.after else name to currentValue
                        }
                        renderDiscoveredSettings()
                    }
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
