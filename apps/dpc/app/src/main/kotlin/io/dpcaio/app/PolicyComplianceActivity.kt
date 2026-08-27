package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Enrollment"
        runEnrollment()
    }

    private fun runEnrollment() {
        val checkingView = TextView(this).apply {
            text = "Checking enterprise enrollment…"
            setPaddingDp(32, 32, 32, 32)
        }
        DpcUiShell.install(this, checkingView)
        setContentView(checkingView)
        Thread {
            val outcome = EnrollmentExecutionRouter(this).execute(intent)
            runOnUiThread {
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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(32, 32, 32, 32)
        }
        body.addView(TextView(this).apply { text = message })
        if (retryable) body.addView(Button(this).apply {
            text = "Retry"
            setOnClickListener { runEnrollment() }
        })
        body.addView(Button(this).apply {
            text = "Enrollment diagnostics"
            setOnClickListener { startActivity(Intent(this@PolicyComplianceActivity, EnrollmentStatusActivity::class.java)) }
        })
        DpcUiShell.install(this, body)
        setContentView(body)
    }
}
