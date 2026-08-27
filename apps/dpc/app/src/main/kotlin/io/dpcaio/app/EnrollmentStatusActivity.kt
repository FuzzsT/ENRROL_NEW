package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EnrollmentStatusActivity : Activity() {
    private var pendingJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Enrollment Diagnostics"
        render()
    }

    private fun render() {
        val session = EnrollmentSessionStore(this).read()
        val snapshot = EnrollmentDiagnosticsSnapshot.capture(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(28, 28, 28, 28)
        }
        body.addView(TextView(this).apply {
            text = if (session == null) {
                "No enrollment session"
            } else buildString {
                appendLine("Session: ${session.sessionId.take(8)}…")
                appendLine("Source: ${session.source}")
                appendLine("Stage: ${session.stage}")
                appendLine("Mode: ${session.requestedMode}")
                appendLine("Policy: ${session.policyProfile}")
                appendLine("Server: ${session.serverUri ?: "local-only"}")
                appendLine("Token fingerprint: ${session.tokenFingerprint ?: "none"}")
                appendLine("Retries: ${session.retryCount} / 4")
                appendLine("Last error: ${session.lastError ?: "none"}")
            }
        })
        body.addView(Button(this).apply {
            text = "Retry"
            isEnabled = session != null
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
