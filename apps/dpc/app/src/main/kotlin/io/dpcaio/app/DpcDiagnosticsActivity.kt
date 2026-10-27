package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
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
        val body = ModernUi.page(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "Diagnostics",
                subtitle = "Runtime capability, ownership and module health at a glance",
                eyebrow = "SYSTEM HEALTH",
            )
        )

        body.addView(ModernUi.section(this, "System health"))
        val health = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                ModernUi.chip(
                    this@DpcDiagnosticsActivity,
                    "API ${snapshot.apiLevel}",
                    ModernUi.Tone.NEUTRAL,
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ModernUi.chip(
                    this@DpcDiagnosticsActivity,
                    if (snapshot.samsungDevice) "Samsung" else "Android",
                    if (snapshot.samsungDevice) ModernUi.Tone.SUCCESS else ModernUi.Tone.NEUTRAL,
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ModernUi.chip(
                    this@DpcDiagnosticsActivity,
                    if (snapshot.shizukuPermissionGranted) "Shizuku ready" else "Shizuku limited",
                    if (snapshot.shizukuPermissionGranted) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING,
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        body.addView(health)

        body.addView(ModernUi.section(this, "Management state"))
        body.addView(
            ModernUi.card(this).apply {
                addView(info("Device", "${snapshot.manufacturer} ${snapshot.model}"))
                addView(info("DPC version", snapshot.dpcVersion))
                addView(info("Device Owner", snapshot.deviceOwner.toString()))
                addView(info("Profile Owner", snapshot.profileOwner.toString()))
                addView(info("Organization-owned", snapshot.organizationOwnedProfile.toString()))
                addView(info("Knox runtime", snapshot.knoxAvailable.toString()))
                addView(info("Knox license", snapshot.knoxLicenseActive.toString()))
                addView(info("Dhizuku compiled", snapshot.dhizukuCompiled.toString()))
            }
        )

        body.addView(ModernUi.section(this, "Offline state"))
        body.addView(
            ModernUi.card(this).apply {
                addView(info("Bundle", snapshot.offlineBundleId ?: "none"))
                addView(info("Stage", snapshot.offlineStage ?: "none"))
                addView(info("Sync pending", snapshot.offlineSyncPending.toString()))
                snapshot.offlineLastError?.let { addView(info("Last error", it, danger = true)) }
            }
        )

        body.addView(ModernUi.section(this, "Module availability"))
        body.addView(
            ModernUi.card(this).apply {
                addView(info("Integrated", snapshot.moduleCounts.integrated.toString()))
                addView(info("Visible", snapshot.moduleCounts.visible.toString()))
                addView(info("Hidden", snapshot.moduleCounts.hidden.toString()))
                addView(info("Available", snapshot.moduleCounts.available.toString()))
                addView(info("Unavailable", snapshot.moduleCounts.unavailable.toString()))
                addView(info("Lab", snapshot.moduleCounts.lab.toString()))
            }
        )

        body.addView(ModernUi.section(this, "Actions"))
        body.addView(
            ModernUi.action(this, "Enrollment diagnostics", primary = true) {
                startActivity(Intent(this@DpcDiagnosticsActivity, EnrollmentStatusActivity::class.java))
            }
        )
        body.addView(
            ModernUi.action(this, "Export dpc-diagnostics.json") {
                export(snapshot.toJson())
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun info(label: String, value: String, danger: Boolean = false): TextView =
        TextView(this).apply {
            text = "$label\n$value"
            setTextColor(if (danger) ModernUi.palette(this@DpcDiagnosticsActivity).danger else ModernUi.palette(this@DpcDiagnosticsActivity).text)
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
            setPadding(0, ModernUi.dp(this@DpcDiagnosticsActivity, 7), 0, ModernUi.dp(this@DpcDiagnosticsActivity, 7))
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
