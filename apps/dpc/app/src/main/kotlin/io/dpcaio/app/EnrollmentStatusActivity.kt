package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class EnrollmentStatusActivity : Activity() {
    private var pendingJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Enrollment Diagnostics"
        render()
    }

    private fun render() {
        val snapshot = EnrollmentDiagnosticsSnapshot.capture(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(28, 28, 28, 28)
        }
        body.addView(TextView(this).apply {
            text = buildString {
                appendLine("Session state: ${snapshot.sessionState}")
                appendLine("Management state: ${snapshot.managementState}")
                appendLine("DPC version: ${snapshot.dpcVersion}")
                appendLine("Provisioning handlers: ${if (snapshot.platformProvisioningHandlersReady) "READY" else "BLOCKED"}")
                appendLine("  GET_PROVISIONING_MODE: ${snapshot.getProvisioningModeHandlerReady}")
                appendLine("  ADMIN_POLICY_COMPLIANCE: ${snapshot.policyComplianceHandlerReady}")
                snapshot.sessionId?.let { appendLine("Session: ${it.take(8)}…") }
                snapshot.source?.let { appendLine("Source: $it") }
                snapshot.stage?.let { appendLine("Stage: $it") }
                snapshot.requestedMode?.let { appendLine("Mode: $it") }
                snapshot.policyProfile?.let { appendLine("Policy: $it") }
                appendLine("Server: ${snapshot.serverUri ?: "local-only / not set"}")
                appendLine("Token fingerprint: ${snapshot.tokenFingerprint ?: "none"}")
                appendLine("Retries: ${snapshot.retryCount} / 4")
                appendLine("Last error: ${snapshot.lastError ?: "none"}")
                snapshot.sessionReadErrorClass?.let { appendLine("Session read error: $it") }
                appendLine("Recommended action: ${snapshot.recommendedAction}")
            }
        })
        body.addView(Button(this).apply {
            text = "Retry"
            isEnabled = snapshot.sessionState == EnrollmentSessionDiagnosticState.READABLE
            setOnClickListener {
                EnrollmentCoordinator.scheduleResume(this@EnrollmentStatusActivity, "MANUAL_RETRY")
                render()
            }
        })
        body.addView(Button(this).apply {
            text = "Export enrollment-diagnostics.json"
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
                putExtra(Intent.EXTRA_TITLE, "enrollment-diagnostics.json")
            },
            REQUEST_EXPORT,
        )
    }

    @Deprecated("Activity result API retained for platform Activity compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val json = pendingJson ?: return
        contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
        pendingJson = null
    }

    companion object { private const val REQUEST_EXPORT = 7101 }
}
