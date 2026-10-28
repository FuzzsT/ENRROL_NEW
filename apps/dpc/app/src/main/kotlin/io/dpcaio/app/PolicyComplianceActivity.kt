package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Enrollment"
        runEnrollment()
    }

    private fun runEnrollment() {
        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Enterprise enrollment",
                subtitle = "Checking enterprise enrollment…",
                eyebrow = "ENROLLMENT",
            )
        )
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.chip(this@PolicyComplianceActivity, "CHECKING", ModernUi.Tone.NEUTRAL))
                addView(ModernUi.body(this@PolicyComplianceActivity, "Validating provisioning input and management state."))
            }
        )
        setContentView(ModernUi.scrollPage(this, body))

        Thread {
            val outcome = runCatching { EnrollmentExecutionRouter(this).execute(intent) }.getOrElse { error ->
                val offlineStore = OfflineDeploymentStore(applicationContext)
                if (offlineStore.hasPendingPersistence()) {
                    applicationContext.sendBroadcast(
                        Intent(applicationContext, OfflineRecoveryReceiver::class.java)
                            .setAction(OfflineInstallStatusReceiver.ACTION_PERSISTENCE_RETRY)
                    )
                    EnrollmentExecutionOutcome.Retryable(
                        "OFFLINE_DEPLOYMENT_STATE_PERSIST_FAILED: ${error.message ?: error.javaClass.simpleName}"
                    )
                } else {
                    EnrollmentExecutionOutcome.Failed(
                        "ENROLLMENT_EXECUTION_FAILED: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (outcome) {
                    is EnrollmentExecutionOutcome.Complete -> {
                        setResult(RESULT_OK)
                        finish()
                    }
                    is EnrollmentExecutionOutcome.Retryable -> renderFailure(outcome.message, retryable = true)
                    is EnrollmentExecutionOutcome.Failed -> renderFailure(outcome.message, retryable = false)
                }
            }
        }.start()
    }

    private fun renderFailure(message: String, retryable: Boolean) {
        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = if (retryable) "Enrollment needs attention" else "Enrollment failed",
                subtitle = "The provisioning result was not complete.",
                eyebrow = "ENROLLMENT",
            )
        )
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.chip(this@PolicyComplianceActivity, if (retryable) "RETRYABLE" else "FAILED", if (retryable) ModernUi.Tone.WARNING else ModernUi.Tone.DANGER))
                addView(ModernUi.body(this@PolicyComplianceActivity, message))
            }
        )
        body.addView(ModernUi.section(this, "Actions"))
        body.addView(
            ModernUi.card(this).apply {
                if (retryable) {
                    addView(ModernUi.action(this@PolicyComplianceActivity, "Retry", primary = true) { runEnrollment() })
                }
                addView(ModernUi.action(this@PolicyComplianceActivity, "Enrollment diagnostics") {
                    startActivity(Intent(this@PolicyComplianceActivity, EnrollmentStatusActivity::class.java))
                })
            }
        )
        setContentView(ModernUi.scrollPage(this, body))
    }
}
