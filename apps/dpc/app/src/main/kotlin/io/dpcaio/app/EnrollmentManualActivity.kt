package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.core.model.EnrollmentSession
import io.dpcaio.core.model.EnrollmentSource
import io.dpcaio.core.model.NormalizedEnrollmentConfig

class EnrollmentManualActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Enrollment Engine"

        val endpoint = EditText(this).apply { hint = "https://enroll.example.com" }
        val token = EditText(this).apply {
            hint = "Enrollment token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val profile = EditText(this).apply { hint = "Policy profile"; setText("default") }
        val organization = EditText(this).apply { hint = "Organization ID (optional)" }
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EnrollmentManualActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("work-profile", "fully-managed"),
            )
        }
        val allowOffline = CheckBox(this).apply { text = "Allow local/offline provisioning" }
        val status = TextView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(28, 28, 28, 28)
            addView(endpoint)
            addView(token)
            addView(profile)
            addView(organization)
            addView(mode)
            addView(allowOffline)
            addView(Button(this@EnrollmentManualActivity).apply {
                text = "Start / Resume enrollment"
                setOnClickListener {
                    val endpointValue = endpoint.text.toString().trim()
                    val tokenValue = token.text.toString().trim()
                    if (!endpointValue.startsWith("https://", ignoreCase = true)) {
                        status.text = "Enrollment endpoint must use https://"
                        return@setOnClickListener
                    }
                    if (tokenValue.isBlank()) {
                        status.text = "Enrollment token is required"
                        return@setOnClickListener
                    }
                    val config = NormalizedEnrollmentConfig(
                        source = EnrollmentSource.MANUAL_TOKEN,
                        requestedMode = mode.selectedItem.toString(),
                        enrollmentToken = tokenValue,
                        policyProfile = profile.text.toString().trim().ifBlank { "default" },
                        serverUri = endpointValue,
                        organizationId = organization.text.toString().trim().ifBlank { null },
                        allowOffline = allowOffline.isChecked,
                    )
                    val initial = EnrollmentSession.new(config)
                    val secretRef = "session:${initial.sessionId}"
                    EnrollmentSecretStore(this@EnrollmentManualActivity).put(
                        secretRef,
                        EnrollmentSecrets(enrollmentToken = tokenValue),
                    )
                    val session = initial.copy(
                        tokenFingerprint = EnrollmentSessionStore.tokenFingerprint(tokenValue),
                        secretRef = secretRef,
                    )
                    EnrollmentSessionStore(this@EnrollmentManualActivity).write(session)
                    token.text?.clear()
                    EnrollmentCoordinator.scheduleResume(this@EnrollmentManualActivity, "MANUAL_TOKEN")
                    status.text = "Enrollment scheduled. Open Enrollment Status for progress."
                }
            })
            addView(status)
            addView(Button(this@EnrollmentManualActivity).apply {
                text = "Enrollment Status"
                setOnClickListener { startActivity(android.content.Intent(this@EnrollmentManualActivity, EnrollmentStatusActivity::class.java)) }
            })
        }
        setContentView(DpcUiShell.scroll(this, body))
    }
}
