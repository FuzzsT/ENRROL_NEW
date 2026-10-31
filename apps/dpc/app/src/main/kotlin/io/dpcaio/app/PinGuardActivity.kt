package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.android.PinGuardVerifier
import java.util.concurrent.Executors

class PinGuardActivity : Activity() {
    private lateinit var currentPin: EditText
    private lateinit var newPin: EditText
    private lateinit var confirmPin: EditText
    private lateinit var status: TextView
    private val actionButtons = mutableListOf<Button>()
    private val executor = Executors.newSingleThreadExecutor()
    private val verifier by lazy { PinGuardVerifier(this) }
    private var busy = false

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

                val setButton = ModernUi.action(this@PinGuardActivity, "Set / Change PIN", primary = true) {
                    setOrChangePin()
                }
                actionButtons += setButton
                addView(setButton)

                addView(ModernUi.spacer(this@PinGuardActivity, 8))
                val verifyButton = ModernUi.action(this@PinGuardActivity, "Verify PIN") {
                    verifyCurrentPin()
                }
                actionButtons += verifyButton
                addView(verifyButton)

                addView(ModernUi.spacer(this@PinGuardActivity, 8))
                val disableButton = ModernUi.action(this@PinGuardActivity, "Disable PIN", destructive = true) {
                    disablePin()
                }
                actionButtons += disableButton
                addView(disableButton)
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun pinField(hint: String): EditText = ModernUi.field(
        this,
        hint,
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    ).apply {
        filters = arrayOf(InputFilter.LengthFilter(MAX_PIN_LENGTH))
    }

    private fun setOrChangePin() {
        if (busy) return
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

        val configured = verifier.isConfigured()
        val current = currentPin.text?.toString().orEmpty().toCharArray()
        val nextChars = next.toCharArray()
        runPinWork {
            try {
                if (configured) {
                    val verified = verifier.verify(current)
                    if (verified != PinGuardResult.SUCCESS) {
                        return@runPinWork "Current PIN verification" to verified
                    }
                } else {
                    current.fill('\u0000')
                }
                "Set / Change PIN" to verifier.configure(nextChars)
            } finally {
                current.fill('\u0000')
                nextChars.fill('\u0000')
            }
        }
    }

    private fun verifyCurrentPin() {
        if (busy) return
        if (!verifier.isConfigured()) {
            showStatus("PIN Guard is not configured.")
            clearSensitiveFields()
            return
        }
        val current = currentPin.text?.toString().orEmpty().toCharArray()
        runPinWork {
            "Verify PIN" to verifier.verify(current)
        }
    }

    private fun disablePin() {
        if (busy) return
        if (!verifier.isConfigured()) {
            showStatus("PIN Guard is not configured.")
            clearSensitiveFields()
            return
        }
        val current = currentPin.text?.toString().orEmpty().toCharArray()
        runPinWork {
            val verified = verifier.verify(current)
            if (verified != PinGuardResult.SUCCESS) {
                "Disable PIN" to verified
            } else {
                "Disable PIN" to verifier.clear()
            }
        }
    }

    private fun runPinWork(operation: () -> Pair<String, PinGuardResult>) {
        if (busy) return
        setBusy(true)
        executor.execute {
            val outcome = runCatching(operation).getOrElse {
                "PIN Guard" to PinGuardResult.STORE_ERROR
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showResult(outcome.first, outcome.second)
                clearSensitiveFields()
                renderState(outcome.second)
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        actionButtons.forEach { it.isEnabled = !busy }
        currentPin.isEnabled = !busy
        newPin.isEnabled = !busy
        confirmPin.isEnabled = !busy
        if (busy) status.text = "PIN Guard: working…"
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
