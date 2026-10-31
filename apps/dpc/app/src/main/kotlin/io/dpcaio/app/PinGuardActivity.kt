package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.android.PinGuardVerifier

class PinGuardActivity : Activity() {
    private lateinit var currentPin: EditText
    private lateinit var newPin: EditText
    private lateinit var confirmPin: EditText
    private lateinit var status: TextView
    private val verifier by lazy { PinGuardVerifier(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "PIN Guard"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "PIN Guard",
                subtitle = "Local authorization for sensitive DPC-AIO actions. The PIN is never stored in plaintext; only a salted PBKDF2 verifier is persisted.",
                eyebrow = "SECURITY",
            )
        )

        body.addView(ModernUi.section(this, "Status"))
        status = ModernUi.outputCard(this, "Checking PIN Guard…")
        body.addView(status)

        body.addView(ModernUi.section(this, "PIN"))
        body.addView(
            ModernUi.card(this).apply {
                currentPin = pinField("Current PIN")
                newPin = pinField("New PIN (6–12 digits)")
                confirmPin = pinField("Confirm new PIN")
                addView(currentPin)
                addView(newPin)
                addView(confirmPin)

                addView(ModernUi.action(this@PinGuardActivity, "Set / Change PIN", primary = true) {
                    setOrChangePin()
                })
                addView(ModernUi.spacer(this@PinGuardActivity, 8))
                addView(ModernUi.action(this@PinGuardActivity, "Verify PIN") {
                    verifyCurrentPin()
                })
                addView(ModernUi.spacer(this@PinGuardActivity, 8))
                addView(ModernUi.action(this@PinGuardActivity, "Disable PIN", destructive = true) {
                    disablePin()
                })
            }
        )

        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@PinGuardActivity, "Security boundary"))
                addView(
                    ModernUi.body(
                        this@PinGuardActivity,
                        "This PIN authorizes DPC-AIO operations only. It does not replace Android's lock screen or bypass platform confirmation and Device Owner rules.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        renderState()
    }

    private fun pinField(hint: String): EditText = ModernUi.field(
        this,
        hint,
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    ).apply {
        filters = arrayOf(InputFilter.LengthFilter(MAX_PIN_LENGTH))
    }

    private fun setOrChangePin() {
        val next = newPin.text?.toString().orEmpty()
        val confirmation = confirmPin.text?.toString().orEmpty()
        if (!PIN_PATTERN.matches(next)) {
            showStatus("New PIN must contain 6–12 digits.")
            clearSensitiveFields()
            return
        }
        if (next != confirmation) {
            showStatus("New PIN and confirmation do not match.")
            clearSensitiveFields()
            return
        }

        if (verifier.isConfigured()) {
            val current = currentPin.text?.toString().orEmpty().toCharArray()
            val verified = verifier.verify(current)
            if (verified != PinGuardResult.SUCCESS) {
                showResult("Current PIN verification", verified)
                clearSensitiveFields()
                return
            }
        }

        val result = verifier.configure(next.toCharArray())
        showResult("Set / Change PIN", result)
        clearSensitiveFields()
        renderState(result)
    }

    private fun verifyCurrentPin() {
        if (!verifier.isConfigured()) {
            showStatus("PIN Guard is not configured.")
            clearSensitiveFields()
            return
        }
        val result = verifier.verify(currentPin.text?.toString().orEmpty().toCharArray())
        showResult("Verify PIN", result)
        clearSensitiveFields()
    }

    private fun disablePin() {
        if (!verifier.isConfigured()) {
            showStatus("PIN Guard is not configured.")
            clearSensitiveFields()
            return
        }
        val verified = verifier.verify(currentPin.text?.toString().orEmpty().toCharArray())
        if (verified != PinGuardResult.SUCCESS) {
            showResult("Disable PIN", verified)
            clearSensitiveFields()
            return
        }
        val result = verifier.clear()
        showResult("Disable PIN", result)
        clearSensitiveFields()
        renderState(result)
    }

    private fun renderState(lastResult: PinGuardResult? = null) {
        val configured = verifier.isConfigured()
        val suffix = lastResult?.let { " • last=$it" }.orEmpty()
        status.text = if (configured) {
            "PIN Guard: configured$suffix"
        } else {
            "PIN Guard: not configured$suffix"
        }
    }

    private fun showResult(action: String, result: PinGuardResult) {
        val detail = when (result) {
            PinGuardResult.SUCCESS -> "success"
            PinGuardResult.NOT_CONFIGURED -> "not configured"
            PinGuardResult.INVALID -> "invalid PIN"
            PinGuardResult.LOCKED_OUT -> "temporarily locked after failed attempts"
            PinGuardResult.STORE_ERROR -> "local secure state error"
        }
        showStatus("$action: $detail")
    }

    private fun showStatus(message: String) {
        status.text = message
    }

    private fun clearSensitiveFields() {
        currentPin.text?.clear()
        newPin.text?.clear()
        confirmPin.text?.clear()
    }

    companion object {
        private const val MAX_PIN_LENGTH = 12
        private val PIN_PATTERN = Regex("^[0-9]{6,12}$")
    }
}
