package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.dpcaio.selfupdate.SelfUpdateInstallPolicy
import io.dpcaio.selfupdate.SelfUpdateState
import io.dpcaio.selfupdate.android.SelfUpdateStore

data class SelfUpdateUiSnapshot(
    val state: SelfUpdateState,
    val message: String,
)

object SelfUpdateUiStateStore {
    private const val PREFS = "self_update_ui_state"
    private const val KEY_STATE = "state"
    private const val KEY_MESSAGE = "message"

    fun read(context: Context): SelfUpdateUiSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = runCatching {
            SelfUpdateState.valueOf(prefs.getString(KEY_STATE, SelfUpdateState.IDLE.name) ?: SelfUpdateState.IDLE.name)
        }.getOrDefault(SelfUpdateState.IDLE)
        return SelfUpdateUiSnapshot(
            state = state,
            message = prefs.getString(KEY_MESSAGE, "Ready") ?: "Ready",
        )
    }

    fun write(context: Context, state: SelfUpdateState, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, state.name)
            .putString(KEY_MESSAGE, message)
            .apply()
    }
}

class SelfUpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val policy = runCatching {
            SelfUpdateInstallPolicy.valueOf(intent.getStringExtra(EXTRA_POLICY) ?: SelfUpdateInstallPolicy.SILENT_IF_ALLOWED.name)
        }.getOrDefault(SelfUpdateInstallPolicy.SILENT_IF_ALLOWED)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                SelfUpdateStore(context).clearCandidate()
                SelfUpdateUiStateStore.write(context, SelfUpdateState.UPDATED, "Update installed successfully")
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                SelfUpdateUiStateStore.write(
                    context,
                    SelfUpdateState.WAITING_USER,
                    "Android requires user confirmation for ${policy.name.lowercase().replace('_', ' ')}",
                )
                confirmationIntent(intent)?.let { confirmation ->
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                }
            }

            else -> {
                val detail = statusMessage.ifBlank { "PackageInstaller status=$status" }
                SelfUpdateUiStateStore.write(context, SelfUpdateState.ERROR, detail)
            }
        }

        context.sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(context.packageName)
        )
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.dpcaio.action.SELF_UPDATE_INSTALL_STATUS"
        const val ACTION_STATE_CHANGED = "io.dpcaio.action.SELF_UPDATE_STATE_CHANGED"
        const val EXTRA_POLICY = "io.dpcaio.extra.SELF_UPDATE_POLICY"
    }
}
