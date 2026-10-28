package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.core.model.EnrollmentSession
import io.dpcaio.core.model.EnrollmentSource
import io.dpcaio.core.model.NormalizedEnrollmentConfig
import java.util.concurrent.atomic.AtomicBoolean

class EnrollmentManualActivity : Activity() {
    companion object {
        private val persistenceInProgress = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Enrollment Engine"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Enrollment Engine",
                subtitle = "Start or resume a managed-device enrollment session with encrypted token storage.",
                eyebrow = "SETUP & ENROLLMENT",
            )
        )

        body.addView(ModernUi.section(this, "Enrollment source"))
        val form = ModernUi.card(this, emphasized = true)
        val endpoint = ModernUi.field(this, "https://enroll.example.com")
        val token = ModernUi.field(
            this,
            "Enrollment token",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        val profile = ModernUi.field(this, "Policy profile").apply { setText("default") }
        val organization = ModernUi.field(this, "Organization ID (optional)")
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EnrollmentManualActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("work-profile", "fully-managed"),
            )
        }
        val allowOffline = CheckBox(this).apply {
            text = "Allow local/offline provisioning"
            setTextColor(ModernUi.palette(this@EnrollmentManualActivity).text)
        }
        form.addView(endpoint)
        form.addView(token)
        form.addView(profile)
        form.addView(organization)
        form.addView(ModernUi.body(this, "Management mode"))
        form.addView(mode)
        form.addView(ModernUi.spacer(this, 8))
        form.addView(allowOffline)
        body.addView(form)

        body.addView(ModernUi.section(this, "Session"))
        val status = TextView(this).apply { text = "Ready to create an enrollment session." }
        body.addView(ModernUi.outputCard(this, status))

        body.addView(
            ModernUi.action(this, "Start / Resume enrollment", primary = true) {
                val endpointValue = endpoint.text.toString().trim()
                val tokenValue = token.text.toString().trim()
                val profileValue = profile.text.toString().trim()
                val organizationValue = organization.text.toString().trim()
                val modeValue = mode.selectedItem.toString()
                val allowOfflineValue = allowOffline.isChecked
                val appContext = applicationContext

                if (!endpointValue.startsWith("https://", ignoreCase = true)) {
                    status.text = "Enrollment endpoint must use https://"
                    return@action
                }
                if (tokenValue.isBlank()) {
                    status.text = "Enrollment token is required"
                    return@action
                }
                if (!persistenceInProgress.compareAndSet(false, true)) {
                    status.text = "Enrollment persistence already in progress"
                    return@action
                }

                status.text = "Saving enrollment securely..."
                Thread {
                    val result = runCatching {
                        val config = NormalizedEnrollmentConfig(
                            source = EnrollmentSource.MANUAL_TOKEN,
                            requestedMode = modeValue,
                            enrollmentToken = tokenValue,
                            policyProfile = profileValue.ifBlank { "default" },
                            serverUri = endpointValue,
                            organizationId = organizationValue.ifBlank { null },
                            allowOffline = allowOfflineValue,
                        )
                        val initial = EnrollmentSession.new(config)
                        val secretRef = "session:${initial.sessionId}"
                        EnrollmentSecretStore(appContext).put(
                            secretRef,
                            EnrollmentSecrets(enrollmentToken = tokenValue),
                        )
                        val session = initial.copy(
                            tokenFingerprint = EnrollmentSessionStore.tokenFingerprint(tokenValue),
                            secretRef = secretRef,
                        )
                        EnrollmentSessionStore(appContext).write(session)
                        EnrollmentCoordinator.scheduleResume(appContext, "MANUAL_TOKEN")
                    }
                    persistenceInProgress.set(false)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        result.fold(
                            onSuccess = {
                                token.text?.clear()
                                status.text = "Enrollment scheduled. Open Enrollment Status for progress."
                            },
                            onFailure = { error ->
                                status.text = "Enrollment persistence failed: ${error.message ?: error.javaClass.simpleName}"
                            },
                        )
                    }
                }.start()
            }
        )
        body.addView(ModernUi.spacer(this, 8))
        body.addView(
            ModernUi.action(this, "Enrollment Status") {
                startActivity(android.content.Intent(this@EnrollmentManualActivity, EnrollmentStatusActivity::class.java))
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
    }
}
