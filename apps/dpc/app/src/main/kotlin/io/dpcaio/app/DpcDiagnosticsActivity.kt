package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DpcDiagnosticsActivity : Activity() {
    private var pendingJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC Diagnostics"
        render()
    }

    private fun render() {
        val snapshot = DpcDiagnosticsSnapshot.capture(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        body.addView(TextView(this).apply {
            text = buildString {
                appendLine("Android API: ${snapshot.apiLevel}")
                appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}")
                appendLine("DPC: ${snapshot.dpcVersion}")
                appendLine("Device Owner: ${snapshot.deviceOwner}")
                appendLine("Profile Owner: ${snapshot.profileOwner}")
                appendLine("Organization-owned: ${snapshot.organizationOwnedProfile}")
                appendLine("Samsung: ${snapshot.samsungDevice}")
                appendLine("Knox runtime: ${snapshot.knoxAvailable}")
                appendLine("Knox license active: ${snapshot.knoxLicenseActive}")
                appendLine("Shizuku binder: ${snapshot.shizukuBinderAlive}")
                appendLine("Shizuku permission: ${snapshot.shizukuPermissionGranted}")
                appendLine("Dhizuku compiled: ${snapshot.dhizukuCompiled}")
                appendLine("Offline bundle: ${snapshot.offlineBundleId ?: "none"}")
                appendLine("Offline stage: ${snapshot.offlineStage ?: "none"}")
                appendLine("Offline sync pending: ${snapshot.offlineSyncPending}")
                snapshot.offlineLastError?.let { appendLine("Offline last error: $it") }
                appendLine("Modules integrated: ${snapshot.moduleCounts.integrated}")
                appendLine("Modules visible: ${snapshot.moduleCounts.visible}")
                appendLine("Modules hidden: ${snapshot.moduleCounts.hidden}")
                appendLine("Modules available: ${snapshot.moduleCounts.available}")
                appendLine("Modules unavailable: ${snapshot.moduleCounts.unavailable}")
                appendLine("Modules lab: ${snapshot.moduleCounts.lab}")
            }
        })
        body.addView(Button(this).apply {
            text = "Enrollment diagnostics"
            setOnClickListener { startActivity(Intent(this@DpcDiagnosticsActivity, EnrollmentStatusActivity::class.java)) }
        })
        body.addView(Button(this).apply {
            text = "Export dpc-diagnostics.json"
            setOnClickListener { export(snapshot.toJson()) }
        })
        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun export(json: String) {
        pendingJson = json
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "dpc-diagnostics.json")
            },
            REQUEST_EXPORT,
        )
    }

    @Deprecated("Activity result API retained to match platform Activity base class")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val json = pendingJson ?: return
        contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
        pendingJson = null
    }

    companion object {
        private const val REQUEST_EXPORT = 7001
    }
}
