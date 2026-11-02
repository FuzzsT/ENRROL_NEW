package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.appmanager.PackageExportFormat
import io.dpcaio.appmanager.PackageExportPlan
import io.dpcaio.appmanager.android.AndroidInstalledPackageReader
import io.dpcaio.appmanager.android.AndroidPackageExporter

class ProcessMonitorActivity : Activity() {
    private val collector by lazy { ProcessSnapshotCollector(this) }
    private val installedPackageReader by lazy { AndroidInstalledPackageReader(this) }
    private val packageExporter by lazy { AndroidPackageExporter(this) }

    private lateinit var processSpinner: Spinner
    private lateinit var details: TextView
    private lateinit var status: TextView
    private var snapshots: List<ProcessSnapshot> = emptyList()
    private var pendingExportPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportPackage = savedInstanceState?.getString(STATE_EXPORT_PACKAGE)
        title = "Process Monitor"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Process Monitor",
                subtitle = "Inspect Android-visible app processes and export one AIO bundle containing APK, splits, readable OBB and a process snapshot.",
                eyebrow = "BEST-EFFORT PROCESS VIEW",
            )
        )

        body.addView(ModernUi.section(this, "Visible processes"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                processSpinner = Spinner(this@ProcessMonitorActivity)
                processSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        renderSelectedProcess()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        renderSelectedProcess()
                    }
                }
                addView(processSpinner)
                addView(ModernUi.spacer(this@ProcessMonitorActivity, 8))
                details = ModernUi.body(this@ProcessMonitorActivity, "No visible process selected")
                details.setTextIsSelectable(true)
                addView(details)
                addView(ModernUi.spacer(this@ProcessMonitorActivity, 8))
                addView(ModernUi.action(this@ProcessMonitorActivity, "Refresh processes", primary = true) { refreshProcesses() })
            }
        )

        body.addView(ModernUi.section(this, "Package snapshot"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@ProcessMonitorActivity,
                        "AIO Bundle is one ZIP file: base APK + split APKs + readable Android/obb files + process-snapshot.json + manifest.json + SHA256SUMS.txt. App-private data is not copied when Android does not expose it.",
                    )
                )
                addView(ModernUi.spacer(this@ProcessMonitorActivity, 8))
                addView(ModernUi.action(this@ProcessMonitorActivity, "Export AIO Bundle", primary = true) { openAioBundleDestination() })
                addView(ModernUi.spacer(this@ProcessMonitorActivity, 8))
                addView(ModernUi.action(this@ProcessMonitorActivity, "Open package in App Updater") { openSelectedInUpdater() })
            }
        )

        body.addView(
            ModernUi.statusCard(
                this,
                "Android visibility boundary",
                "Process discovery uses ActivityManager.runningAppProcesses. Newer Android releases and OEM policy can restrict which processes are visible; the monitor reports only what the platform exposes.",
                ModernUi.Tone.NEUTRAL,
            )
        )

        body.addView(ModernUi.section(this, "Status"))
        status = ModernUi.outputCard(this, "Ready")
        status.setTextIsSelectable(true)
        body.addView(status)

        setContentView(ModernUi.scrollPage(this, body))
        refreshProcesses()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingExportPackage?.let { outState.putString(STATE_EXPORT_PACKAGE, it) }
        super.onSaveInstanceState(outState)
    }

    private fun refreshProcesses() {
        val previous = selectedSnapshot()?.processName
        snapshots = runCatching { collector.collect() }.getOrElse { emptyList() }
        processSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, snapshots)
        if (previous != null) {
            val index = snapshots.indexOfFirst { it.processName == previous }
            if (index >= 0) processSpinner.setSelection(index)
        }
        renderSelectedProcess()
        status.text = "Visible processes: ${snapshots.size}"
    }

    private fun selectedSnapshot(): ProcessSnapshot? =
        if (::processSpinner.isInitialized) snapshots.getOrNull(processSpinner.selectedItemPosition) else null

    private fun selectedPackage(): String? = selectedSnapshot()?.primaryPackage

    private fun renderSelectedProcess() {
        if (!::details.isInitialized) return
        val snapshot = selectedSnapshot()
        if (snapshot == null) {
            details.text = "No visible process selected"
            return
        }
        details.text = buildString {
            appendLine("Process: ${snapshot.processName}")
            appendLine("PID: ${snapshot.pid}")
            appendLine("UID: ${snapshot.uid}")
            appendLine("Importance: ${snapshot.importance}")
            appendLine("Packages: ${snapshot.packages.joinToString().ifBlank { "None reported" }}")
            append("Export target: ${snapshot.primaryPackage ?: "No package mapping"}")
        }
    }

    private fun openSelectedInUpdater() {
        val packageName = selectedPackage()
        if (packageName.isNullOrBlank()) {
            status.text = "Selected process has no package mapping"
            return
        }
        startActivity(
            Intent(this, AppPackageManagerActivity::class.java)
                .putExtra(AppPackageManagerActivity.EXTRA_MODE, AppPackageManagerActivity.MODE_UPDATER)
                .putExtra(AppPackageManagerActivity.EXTRA_TARGET_PACKAGE, packageName)
        )
    }

    private fun openAioBundleDestination() {
        val packageName = selectedPackage()
        if (packageName.isNullOrBlank()) {
            status.text = "Selected process has no package mapping"
            return
        }
        pendingExportPackage = packageName
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "$packageName-process-snapshot.dpcaio.zip")
        }
        status.text = "Choose destination for $packageName • AIO_BUNDLE"
        @Suppress("DEPRECATION")
        startActivityForResult(picker, REQ_EXPORT_AIO)
    }

    @Deprecated("Compatibility with classic Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXPORT_AIO || resultCode != RESULT_OK) return
        val destination = data?.data ?: return
        val packageName = pendingExportPackage
        pendingExportPackage = null
        if (packageName.isNullOrBlank()) {
            status.text = "Export request expired; select a process again"
            return
        }
        exportAioBundle(packageName, destination)
    }

    private fun exportAioBundle(packageName: String, destination: Uri) {
        status.text = "Exporting $packageName as AIO_BUNDLE…"
        Thread {
            val summary = runCatching {
                val artifacts = installedPackageReader.read(packageName)
                val snapshotEntry = collector.snapshotEntryForPackage(packageName)
                val plan = PackageExportPlan.forFormat(
                    format = PackageExportFormat.AIO_BUNDLE,
                    artifacts = artifacts,
                    generatedTextEntries = mapOf(snapshotEntry),
                )
                val result = packageExporter.export(plan, destination)
                "Export complete • AIO_BUNDLE • $packageName • files=${result.sha256ByEntry.size} • OBB=${result.obbIncluded}"
            }.getOrElse { error ->
                "EXPORT_FAILED: ${error.javaClass.simpleName}: ${error.message}"
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) status.text = summary
            }
        }.start()
    }

    companion object {
        private const val REQ_EXPORT_AIO = 4817
        private const val STATE_EXPORT_PACKAGE = "process_monitor_export_package"
    }
}
