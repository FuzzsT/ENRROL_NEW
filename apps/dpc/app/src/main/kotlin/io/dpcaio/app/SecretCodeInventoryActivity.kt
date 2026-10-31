package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.app.engineering.SecretCodeInventoryScanner
import io.dpcaio.engineering.SecretCodeInventoryRecord
import io.dpcaio.engineering.SecretCodeInventoryStatus

class SecretCodeInventoryActivity : Activity() {
    private lateinit var packageFilter: EditText
    private lateinit var output: TextView
    private var allRecords: List<SecretCodeInventoryRecord> = emptyList()
    private var scannedPackages: Int = 0
    private var extractionFailures: Int = 0
    private var activeStatus: SecretCodeInventoryStatus? = null
    private var scanRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Secret Code Inventory"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Secret Code Inventory",
                subtitle = "Read-only inventory of declared android_secret_code receivers from installed base and split APKs, verified against live PackageManager routing.",
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
                packageFilter = ModernUi.field(this@SecretCodeInventoryActivity, "Package filter")
                packageFilter.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                addView(packageFilter)
                addView(ModernUi.spacer(this@SecretCodeInventoryActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Scan installed apps", primary = true) { scanInstalledApps() },
                        ModernUi.action(this@SecretCodeInventoryActivity, "All") { setStatusFilter(null) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Verified") { setStatusFilter(SecretCodeInventoryStatus.VERIFIED) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Protected") { setStatusFilter(SecretCodeInventoryStatus.PERMISSION_GATED) },
                    )
                )
                addView(
                    ModernUi.actionRow(
                        this@SecretCodeInventoryActivity,
                        ModernUi.action(this@SecretCodeInventoryActivity, "Disabled") { setStatusFilter(SecretCodeInventoryStatus.DISABLED) },
                        ModernUi.action(this@SecretCodeInventoryActivity, "Pattern only") { setStatusFilter(SecretCodeInventoryStatus.PATTERN_ONLY) },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Inventory results"))
        output = TextView(this).apply { text = "Press Scan installed apps to build the read-only inventory." }
        body.addView(ModernUi.outputCard(this, output))
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun scanInstalledApps() {
        if (scanRunning) return
        scanRunning = true
        output.text = "Scanning installed base and split APK manifests…"
        Thread {
            val result = runCatching { SecretCodeInventoryScanner(this).scan() }
            runOnUiThread {
                scanRunning = false
                result.onSuccess { scan ->
                    allRecords = scan.records
                    scannedPackages = scan.scannedPackages
                    extractionFailures = scan.extractionFailures
                    render()
                }.onFailure { error ->
                    output.text = "Inventory scan failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
                }
            }
        }.start()
    }

    private fun setStatusFilter(status: SecretCodeInventoryStatus?) {
        activeStatus = status
        render()
    }

    private fun render() {
        if (!::output.isInitialized) return
        val packageNeedle = if (::packageFilter.isInitialized) packageFilter.text.toString().trim().lowercase() else ""
        val visible = allRecords.filter { record ->
            (activeStatus == null || record.status == activeStatus) &&
                (packageNeedle.isEmpty() || record.packageName.lowercase().contains(packageNeedle))
        }

        output.text = buildString {
            appendLine("SECRET CODE INVENTORY")
            appendLine("packages=$scannedPackages records=${allRecords.size} visible=${visible.size} extractionFailures=$extractionFailures")
            appendLine("filter=${activeStatus?.name ?: "ALL"} package=${packageNeedle.ifEmpty { "*" }}")
            if (visible.isEmpty()) {
                append("No matching declarations in the current filter.")
                return@buildString
            }
            visible.forEach { record ->
                appendLine()
                appendLine("[${record.status}] ${record.code ?: record.rawHost ?: "<pattern>"}")
                appendLine("  ${record.packageName}/${record.receiverClassName}")
                appendLine("  actions=${record.actions.sorted().joinToString()}")
                appendLine("  enabled=${record.enabled} exported=${record.exported} permission=${record.requiredPermission ?: "none"}")
                appendLine("  sourceKinds=${record.sourceKinds.sortedBy { it.name }.joinToString()} sources=${record.sourceApkNames.sorted().joinToString()}")
            }
        }
    }
}
