package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

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
        val body = ModernUi.page(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "Enrollment Status",
                subtitle = "Session state, retry information and exportable diagnostics.",
                eyebrow = "ENROLLMENT DIAGNOSTICS",
            )
        )

        body.addView(ModernUi.section(this, "Current session"))
        val sessionCard = ModernUi.card(this, emphasized = session != null)
        if (session == null) {
            sessionCard.addView(ModernUi.title(this, "No enrollment session"))
            sessionCard.addView(ModernUi.body(this, "Start enrollment from the Enrollment Engine to create a session."))
            sessionCard.addView(ModernUi.chip(this, "IDLE", ModernUi.Tone.NEUTRAL))
        } else {
            sessionCard.addView(ModernUi.title(this, "Session ${session.sessionId.take(8)}…"))
            sessionCard.addView(
                ModernUi.body(
                    this,
                    buildString {
                        appendLine("Stage: ${session.stage}")
                        appendLine("Source: ${session.source}")
                        appendLine("Mode: ${session.requestedMode}")
                        appendLine("Policy: ${session.policyProfile}")
                        appendLine("Server: ${session.serverUri ?: "local-only"}")
                        appendLine("Token fingerprint: ${session.tokenFingerprint ?: "none"}")
                        appendLine("Retries: ${session.retryCount} / 4")
                        append("Last error: ${session.lastError ?: "none"}")
                    },
                )
            )
            val tone = if (session.lastError == null) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING
            sessionCard.addView(ModernUi.chip(this, session.stage.toString(), tone))
        }
        body.addView(sessionCard)

        body.addView(ModernUi.section(this, "Actions"))
        body.addView(
            ModernUi.action(this, "Retry", primary = true) {
                EnrollmentCoordinator.scheduleResume(this@EnrollmentStatusActivity, "MANUAL_RETRY")
                render()
            }.apply { isEnabled = session != null }
        )
        body.addView(ModernUi.spacer(this, 8))
        body.addView(
            ModernUi.action(this, "Export enrollment-diagnostics.json") {
                export(snapshot.toJson())
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
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
