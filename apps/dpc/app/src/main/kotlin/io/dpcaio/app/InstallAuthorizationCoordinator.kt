package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.android.PinGuardVerifier
import java.util.concurrent.Executors

class InstallAuthorizationCoordinator(
    context: Context,
    private val pinGuard: PinGuardVerifier = PinGuardVerifier(context),
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var authorizationGranted = false
    @Volatile private var promptVisible = false

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) authorizationGranted = false
    }

    fun authorize(pin: CharArray): PinGuardResult {
        if (!isEnabled()) {
            pin.fill('\u0000')
            authorizationGranted = true
            return PinGuardResult.SUCCESS
        }
        if (!pinGuard.isConfigured()) {
            pin.fill('\u0000')
            return PinGuardResult.NOT_CONFIGURED
        }
        val result = pinGuard.verify(pin)
        if (result == PinGuardResult.SUCCESS) authorizationGranted = true
        return result
    }

    @Synchronized
    fun consumeAuthorization(): Boolean {
        if (!isEnabled()) return true
        if (!authorizationGranted) return false
        authorizationGranted = false
        return true
    }

    fun requestInstallAuthorization(context: Context): Boolean {
        if (!isEnabled()) {
            authorizationGranted = true
            notifyQueueChanged()
            return true
        }
        val activity = context as? Activity ?: return false
        if (activity.isFinishing || activity.isDestroyed || promptVisible) return false
        if (!pinGuard.isConfigured()) {
            promptVisible = true
            AlertDialog.Builder(activity)
                .setTitle("Require PIN for installs")
                .setMessage("PIN Guard is not configured. Configure a PIN before installing queued packages.")
                .setPositiveButton("Configure PIN") { _, _ ->
                    promptVisible = false
                    activity.startActivity(Intent(activity, PinGuardActivity::class.java))
                }
                .setNegativeButton("Cancel") { _, _ -> promptVisible = false }
                .setOnCancelListener { promptVisible = false }
                .show()
            return true
        }

        val field = EditText(activity).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(MAX_PIN_LENGTH))
        }
        promptVisible = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Require PIN for installs")
            .setMessage("Enter PIN Guard PIN to authorize exactly one queued install.")
            .setView(field)
            .setPositiveButton("Authorize", null)
            .setNegativeButton("Cancel") { _, _ -> promptVisible = false }
            .setOnCancelListener { promptVisible = false }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = field.text?.toString().orEmpty().toCharArray()
                field.text?.clear()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                executor.execute {
                    val result = try {
                        authorize(pin)
                    } finally {
                        pin.fill('\u0000')
                    }
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) {
                            promptVisible = false
                            return@runOnUiThread
                        }
                        if (result == PinGuardResult.SUCCESS) {
                            promptVisible = false
                            dialog.dismiss()
                            notifyQueueChanged()
                        } else {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            field.error = when (result) {
                                PinGuardResult.INVALID -> "Invalid PIN"
                                PinGuardResult.LOCKED_OUT -> "PIN Guard is temporarily locked"
                                PinGuardResult.NOT_CONFIGURED -> "PIN Guard is not configured"
                                PinGuardResult.STORE_ERROR -> "PIN Guard storage error"
                                PinGuardResult.SUCCESS -> null
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
        return true
    }

    private fun notifyQueueChanged() {
        app.sendBroadcast(
            Intent(AppInstallStatusReceiver.ACTION_QUEUE_CHANGED)
                .setPackage(app.packageName)
        )
    }

    companion object {
        private const val PREFS_NAME = "install_pin_guard"
        private const val KEY_ENABLED = "enabled"
        private const val MAX_PIN_LENGTH = 12
    }
}
