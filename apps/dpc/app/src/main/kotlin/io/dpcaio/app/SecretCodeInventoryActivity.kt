package io.dpcaio.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import io.dpcaio.app.engineering.SecretCodeInventoryItem
import io.dpcaio.app.engineering.SecretCodeInventoryRepository
import io.dpcaio.app.engineering.SecretCodeRuntimeVerifier
import io.dpcaio.app.engineering.SecretCodeScanEvent
import io.dpcaio.app.engineering.SecretCodeScanProgress
import io.dpcaio.app.engineering.SecretCodeVerification
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SecretCodeInventoryActivity : Activity() {
    private lateinit var searchField: EditText
    private lateinit var selectedRowField: EditText
    private lateinit var statusText: TextView
    private lateinit var output: TextView
    private lateinit var repository: SecretCodeInventoryRepository
    private lateinit var verifier: SecretCodeRuntimeVerifier

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelRequested = AtomicBoolean(false)
    private var receiverRegistered = false
    private var scanRunning = false
    private var allItems: List<SecretCodeInventoryItem> = emptyList()
    private var scannedPackages = 0
    private var diagnostics = 0
    private var activeFilter = InventoryFilter.ALL
    private var sortMode = SortMode.PACKAGE

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action !in PACKAGE_CHANGE_ACTIONS) return
            repository.invalidate()
            if (scanRunning) cancelRequested.set(true)
            statusText.text = "Package inventory changed; cache invalidated. Run Scan installed apps again."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Secret Code Inventory"

        repository = SecretCodeInventoryRepository(this)
        verifier = SecretCodeRuntimeVerifier(this)
        activeFilter = savedInstanceState?.getString(STATE_FILTER)
            ?.let { runCatching { InventoryFilter.valueOf(it) }.getOrNull() }
            ?: InventoryFilter.ALL
        sortMode = savedInstanceState?.getString(STATE_SORT)
            ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: SortMode.PACKAGE

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Secret Code Inventory",
                subtitle = "Read-only inventory of declared android_secret_code receivers from installed base and split APKs, with PackageManager re-probing but no secret-code execution.",
                eyebrow = "MANIFEST • RECEIVERS • READ ONLY",
            )
        )

        body.addView(ModernUi.section(this, "Inventory controls"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(
                    ModernUi.body(
                        this@SecretCodeInventoryActivity,
                        "Scanning never executes a secret code. Unknown, protected and risky declarations remain display-only.",
                    )
                )

                searchField = ModernUi.field(this@SecretCodeInventoryActivity, "Search package / component / code")
                searchField.setText(savedInstanceState?.getString(STATE_SEARCH).orEmpty())
                searchField.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(searchField)

                selectedRowField = ModernUi.field(this@SecretCodeInventoryActivity, "Selected row #")
                selectedRowField.setText(savedInstanceState?.getString(STATE_SELECTED_ROW).orEmpty())
                addView(selectedRowField)
                addView(ModernUi.spacer(this@SecretCodeInventoryActivity, 8))

                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Scan installed apps", primary = true) { scanInstalledApps() },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Cancel scan") { cancelScan() },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "All") { setFilter(InventoryFilter.ALL) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Verified") { setFilter(InventoryFilter.VERIFIED) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Declared only") { setFilter(InventoryFilter.DECLARED_ONLY) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Protected") { setFilter(InventoryFilter.PROTECTED) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Disabled") { setFilter(InventoryFilter.DISABLED) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Pattern only") { setFilter(InventoryFilter.PATTERN_ONLY) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Sort package") { setSort(SortMode.PACKAGE) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Sort code") { setSort(SortMode.CODE) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Sort status") { setSort(SortMode.STATUS) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Re-probe selected") { reprobeSelected() },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Copy code") { copySelected(CopyMode.CODE) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Copy component") { copySelected(CopyMode.COMPONENT) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Copy record") { copySelected(CopyMode.RECORD) },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Inventory status"))
        statusText = TextView(this).apply { text = "Idle. Read-only scan has not started." }
        body.addView(ModernUi.outputCard(this, statusText))

        body.addView(ModernUi.section(this, "Inventory results"))
        output = TextView(this).apply { text = "Press Scan installed apps to build the read-only inventory." }
        body.addView(ModernUi.outputCard(this, output))
        setContentView(ModernUi.scrollPage(this, body))

        registerPackageChangeReceiver()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SEARCH, if (::searchField.isInitialized) searchField.text.toString() else "")
        outState.putString(STATE_SELECTED_ROW, if (::selectedRowField.isInitialized) selectedRowField.text.toString() else "")
        outState.putString(STATE_FILTER, activeFilter.name)
        outState.putString(STATE_SORT, sortMode.name)
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        executor.shutdownNow()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(packageChangeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun scanInstalledApps() {
        if (scanRunning) return
        scanRunning = true
        cancelRequested.set(false)
        diagnostics = 0
        scannedPackages = 0
        statusText.text = "Scanning installed base and split APK manifests…"

        executor.execute {
            var lastProgress: SecretCodeScanProgress? = null
            val result = runCatching {
                repository.scan(
                    cancelled = { cancelRequested.get() },
                    onEvent = { event ->
                        when (event) {
                            is SecretCodeScanEvent.Progress -> {
                                lastProgress = event.progress
                                runOnUiThread {
                                    scannedPackages = event.progress.scannedPackages
                                    statusText.text = "Scanning ${event.progress.scannedPackages}/${event.progress.totalPackages}; candidates=${event.progress.candidates}"
                                }
                            }
                            is SecretCodeScanEvent.Diagnostic -> diagnostics++
                            is SecretCodeScanEvent.Candidate -> Unit
                            SecretCodeScanEvent.Cancelled -> Unit
                        }
                    },
                )
            }

            runOnUiThread {
                scanRunning = false
                result.onSuccess { items ->
                    allItems = items
                    scannedPackages = lastProgress?.scannedPackages ?: scannedPackages
                    statusText.text = if (cancelRequested.get()) {
                        "Scan cancelled; showing ${items.size} partial read-only records."
                    } else {
                        "Scan complete; records=${items.size}, diagnostics=$diagnostics."
                    }
                    render()
                }.onFailure { error ->
                    statusText.text = "Inventory scan failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun cancelScan() {
        if (!scanRunning) {
            statusText.text = "No scan is running."
            return
        }
        cancelRequested.set(true)
        statusText.text = "Cancellation requested…"
    }

    private fun reprobeSelected() {
        if (scanRunning) {
            statusText.text = "Finish or cancel the current scan before re-probing a row."
            return
        }
        val selected = selectedVisibleItem() ?: return
        statusText.text = "Re-probing ${selected.packageName}/${selected.receiverClassName}…"
        executor.execute {
            val result = runCatching { verifier.verify(selected) }
            runOnUiThread {
                result.onSuccess { updated ->
                    val index = allItems.indexOf(selected)
                    if (index >= 0) {
                        allItems = allItems.toMutableList().also { it[index] = updated }
                    }
                    repository.invalidate()
                    statusText.text = "Re-probe complete: ${updated.verification}."
                    render()
                }.onFailure { error ->
                    statusText.text = "Re-probe failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun copySelected(mode: CopyMode) {
        val selected = selectedVisibleItem() ?: return
        val text = when (mode) {
            CopyMode.CODE -> selected.normalizedCode ?: selected.rawHost.orEmpty()
            CopyMode.COMPONENT -> "${selected.packageName}/${selected.receiverClassName}"
            CopyMode.RECORD -> formatRecord(selected)
        }
        if (text.isBlank()) {
            statusText.text = "Selected row has no copyable value for ${mode.name.lowercase()}."
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Secret Code Inventory", text))
        Toast.makeText(this, "Copied ${mode.name.lowercase()}", Toast.LENGTH_SHORT).show()
    }

    private fun selectedVisibleItem(): SecretCodeInventoryItem? {
        val row = selectedRowField.text.toString().trim().toIntOrNull()
        val visible = visibleItems()
        if (row == null || row !in 1..visible.size) {
            statusText.text = "Enter a selected row number from 1 to ${visible.size}."
            return null
        }
        return visible[row - 1]
    }

    private fun setFilter(filter: InventoryFilter) {
        activeFilter = filter
        render()
    }

    private fun setSort(mode: SortMode) {
        sortMode = mode
        render()
    }

    private fun visibleItems(): List<SecretCodeInventoryItem> {
        val needle = if (::searchField.isInitialized) searchField.text.toString().trim().lowercase() else ""
        val filtered = allItems.filter { item ->
            matchesFilter(item) && (
                needle.isEmpty() ||
                    item.packageName.lowercase().contains(needle) ||
                    item.receiverClassName.lowercase().contains(needle) ||
                    item.normalizedCode.orEmpty().lowercase().contains(needle) ||
                    item.rawHost.orEmpty().lowercase().contains(needle)
                )
        }
        return when (sortMode) {
            SortMode.PACKAGE -> filtered.sortedWith(
                compareBy<SecretCodeInventoryItem> { it.packageName }
                    .thenBy { it.normalizedCode ?: it.rawHost ?: "" }
                    .thenBy { it.receiverClassName }
            )
            SortMode.CODE -> filtered.sortedWith(
                compareBy<SecretCodeInventoryItem> { it.normalizedCode ?: it.rawHost ?: "" }
                    .thenBy { it.packageName }
                    .thenBy { it.receiverClassName }
            )
            SortMode.STATUS -> filtered.sortedWith(
                compareBy<SecretCodeInventoryItem> { it.verification.name }
                    .thenBy { it.packageName }
                    .thenBy { it.normalizedCode ?: it.rawHost ?: "" }
            )
        }
    }

    private fun matchesFilter(item: SecretCodeInventoryItem): Boolean = when (activeFilter) {
        InventoryFilter.ALL -> true
        InventoryFilter.VERIFIED -> item.verification == SecretCodeVerification.VERIFIED
        InventoryFilter.DECLARED_ONLY -> item.verification == SecretCodeVerification.DECLARED_ONLY
        InventoryFilter.PROTECTED -> item.verification in setOf(
            SecretCodeVerification.PERMISSION_GATED,
            SecretCodeVerification.NOT_EXPORTED,
            SecretCodeVerification.NOT_VISIBLE,
        )
        InventoryFilter.DISABLED -> item.verification == SecretCodeVerification.DISABLED
        InventoryFilter.PATTERN_ONLY -> item.verification == SecretCodeVerification.PATTERN_ONLY
    }

    private fun render() {
        if (!::output.isInitialized) return
        val visible = visibleItems()
        output.text = buildString {
            appendLine("SECRET CODE INVENTORY")
            appendLine("scannedPackages=$scannedPackages records=${allItems.size} visible=${visible.size} diagnostics=$diagnostics")
            appendLine("filter=${activeFilter.name} sort=${sortMode.name} search=${searchField.text.toString().trim().ifEmpty { "*" }}")
            if (visible.isEmpty()) {
                append("No matching declarations in the current filter.")
                return@buildString
            }
            visible.forEachIndexed { index, item ->
                appendLine()
                appendLine("${index + 1}. [${item.verification}] ${item.normalizedCode ?: item.rawHost ?: "<pattern>"}")
                appendLine("  ${item.packageName}/${item.receiverClassName}")
                appendLine("  actions=${item.actions.sorted().joinToString()}")
                appendLine("  enabled=${item.enabled} exported=${item.exported} requiredPermission=${item.requiredPermission ?: "none"}")
                appendLine("  sourceKind=${item.sourceKind} sourceApkName=${item.sourceApkName}")
            }
        }
    }

    private fun formatRecord(item: SecretCodeInventoryItem): String = buildString {
        appendLine("package=${item.packageName}")
        appendLine("component=${item.receiverClassName}")
        appendLine("code=${item.normalizedCode ?: item.rawHost ?: ""}")
        appendLine("verification=${item.verification}")
        appendLine("enabled=${item.enabled}")
        appendLine("exported=${item.exported}")
        appendLine("requiredPermission=${item.requiredPermission ?: ""}")
        appendLine("sourceKind=${item.sourceKind}")
        appendLine("sourceApkName=${item.sourceApkName}")
        append("actions=${item.actions.sorted().joinToString()}")
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
        receiverRegistered = true
    }

    private enum class InventoryFilter {
        ALL,
        VERIFIED,
        DECLARED_ONLY,
        PROTECTED,
        DISABLED,
        PATTERN_ONLY,
    }

    private enum class SortMode {
        PACKAGE,
        CODE,
        STATUS,
    }

    private enum class CopyMode {
        CODE,
        COMPONENT,
        RECORD,
    }

    companion object {
        private const val STATE_SEARCH = "secret_code_inventory_search"
        private const val STATE_SELECTED_ROW = "secret_code_inventory_selected_row"
        private const val STATE_FILTER = "secret_code_inventory_filter"
        private const val STATE_SORT = "secret_code_inventory_sort"
        private val PACKAGE_CHANGE_ACTIONS = setOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
        )
    }
}
