package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
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
        val firmware = snapshot.samsungFirmware
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 24, 24, 24)
        }
        body.addView(TextView(this).apply {
            text = buildString {
                appendLine("Android API: ${snapshot.apiLevel}")
                appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}")
                appendLine("DPC: ${snapshot.dpcVersion}")
                appendLine("Management state: ${snapshot.managementState}")
                appendLine("Owner policy ready: ${snapshot.ownerPolicyReady}")
                appendLine("Device Owner: ${snapshot.deviceOwner}")
                appendLine("Profile Owner: ${snapshot.profileOwner}")
                appendLine("Organization-owned: ${snapshot.organizationOwnedProfile}")
                appendLine("Samsung: ${snapshot.samsungDevice}")
                appendLine("Knox runtime: ${snapshot.knoxAvailable}")
                appendLine("Knox license active: ${snapshot.knoxLicenseActive}")
                appendLine()
                appendLine("Samsung firmware profile:")
                appendLine("Sales code: ${firmware.salesCode ?: "unknown"}")
                appendLine("Carrier ID: ${firmware.carrierId ?: "unknown"}")
                appendLine("Multi-CSC: ${firmware.multiCsc ?: "unknown"}")
                appendLine("Country ISO: ${firmware.countryIso ?: "unknown"}")
                appendLine("OMC path: ${firmware.omcPath ?: "unavailable"}")
                appendLine("OMC etc path: ${firmware.omcEtcPath ?: "unavailable"}")
                appendLine("OMC build: ${firmware.omcBuildVersion ?: "unavailable"}")
                appendLine("Build PDA: ${firmware.buildPda ?: "unavailable"}")
                appendLine("Build incremental: ${firmware.buildIncremental ?: "unavailable"}")
                appendLine("System-property probe: ${if (firmware.propertyAccessAvailable) "available" else "blocked/unavailable"}")
                appendLine("Carrier provisioning layer: ${if (firmware.carrierProvisioningPresent) "detected" else "not observed"}")
                appendLine("Carrier evidence packages: ${firmware.carrierPackageCount}")
                appendLine("Samsung connectivity overlay: ${if (firmware.connectivityOverlayPresent) "detected" else "not observed"}")
                appendLine("Firmware package probes: ${firmware.observedPackageCount}/${firmware.packages.size} observed")
                firmware.packages.forEach { probe ->
                    val state = if (probe.installed) {
                        "installed, enabled=${probe.enabled}, system=${probe.systemApp}"
                    } else {
                        "not observed"
                    }
                    appendLine("  ${probe.packageName} — ${probe.packageClass} — ${probe.role}: $state")
                }
                appendLine()
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
                appendLine("Module surfaces executable: ${snapshot.moduleCounts.available}")
                appendLine("Module surfaces blocked: ${snapshot.moduleCounts.unavailable}")
                appendLine("Module availability semantics: ${snapshot.moduleAvailabilitySemantics}")
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
        setContentView(DpcUiShell.scroll(this, body))
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
