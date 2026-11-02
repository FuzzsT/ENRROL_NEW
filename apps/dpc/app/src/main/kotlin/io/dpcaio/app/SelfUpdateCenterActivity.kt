package io.dpcaio.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import io.dpcaio.selfupdate.SelfUpdateState
import io.dpcaio.selfupdate.android.SelfUpdateOperationCoordinator
import io.dpcaio.selfupdate.android.SelfUpdateStore

class SelfUpdateCenterActivity : Activity() {
    private val store by lazy { SelfUpdateStore(this) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val operations = SelfUpdateOperationCoordinator
    private var registered = false

    private val stateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Update Center"
        render()
    }

    override fun onStart() {
        super.onStart()
        if (!registered) {
            val filter = IntentFilter(SelfUpdateStatusReceiver.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(stateChangedReceiver, filter)
            }
            registered = true
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onStop() {
        if (registered) {
            runCatching { unregisterReceiver(stateChangedReceiver) }
            registered = false
        }
        super.onStop()
    }

    private fun render() {
        val snapshot = SelfUpdateUiStateStore.read(this)
        val candidate = store.loadCandidate()
        val busy = operations.isBusy()
        val body = ModernUi.page(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "Update Center",
                subtitle = "Normal self-update workflow with verification, recovery and explicit automation controls.",
                eyebrow = snapshot.state.name.replace('_', ' '),
            )
        )

        body.addView(ModernUi.section(this, "Current state"))
        body.addView(
            ModernUi.statusCard(
                this,
                snapshot.state.name.replace('_', ' '),
                snapshot.message,
                tone(snapshot.state),
            )
        )
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.detailRow(this@SelfUpdateCenterActivity, "Verified candidate", if (candidate?.verified == true) "YES" else "No"))
                addView(ModernUi.detailRow(this@SelfUpdateCenterActivity, "Cached APK", if (candidate?.apkFile?.isFile == true) candidate.apkFile.name else "None"))
                addView(ModernUi.detailRow(this@SelfUpdateCenterActivity, "Target version", candidate?.manifest?.let { "${it.versionName} (${it.versionCode})" } ?: "—"))
            }
        )

        body.addView(ModernUi.section(this, "Automation"))
        body.addView(
            ModernUi.card(this).apply {
                addView(CheckBox(this@SelfUpdateCenterActivity).apply {
                    text = "Automatically install a verified update when silent install is supported"
                    isChecked = prefs.getBoolean(KEY_AUTO_SILENT, false)
                    isEnabled = !busy
                    setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(KEY_AUTO_SILENT, checked).apply() }
                })
                addView(
                    ModernUi.detailRow(
                        this@SelfUpdateCenterActivity,
                        "Safe default",
                        "Downloads are still verified before PackageInstaller submission",
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Actions"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.action(this@SelfUpdateCenterActivity, "Check now", primary = true) {
                    openUpdater(checkNow = true)
                })
                addView(ModernUi.action(this@SelfUpdateCenterActivity, "Open updater") {
                    openUpdater(checkNow = false)
                })
                addView(ModernUi.action(this@SelfUpdateCenterActivity, "Retry / re-check") {
                    openUpdater(checkNow = true)
                })
                addView(ModernUi.action(this@SelfUpdateCenterActivity, "Discard cached candidate", destructive = true) {
                    if (!operations.isBusy()) {
                        store.clearCandidate()
                        SelfUpdateUiStateStore.write(this@SelfUpdateCenterActivity, SelfUpdateState.IDLE, "Cached update discarded; ready for a fresh check")
                    }
                    render()
                }.apply { isEnabled = candidate != null && !busy })
            }
        )

        body.addView(
            ModernUi.statusCard(
                this,
                "Recovery behavior",
                "A failed download or install no longer needs Emergency mode. Use the normal Update Center to re-check, re-download, or clear only the pending candidate.",
                ModernUi.Tone.NEUTRAL,
            )
        )
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun openUpdater(checkNow: Boolean) {
        startActivity(
            Intent(this, SelfUpdateActivity::class.java).apply {
                if (checkNow) putExtra(SelfUpdateActivity.EXTRA_CHECK_NOW, true)
            }
        )
    }

    private fun tone(state: SelfUpdateState): ModernUi.Tone = when (state) {
        SelfUpdateState.UP_TO_DATE, SelfUpdateState.VERIFIED, SelfUpdateState.UPDATED -> ModernUi.Tone.SUCCESS
        SelfUpdateState.ERROR -> ModernUi.Tone.DANGER
        SelfUpdateState.UPDATE_AVAILABLE, SelfUpdateState.WAITING_USER -> ModernUi.Tone.WARNING
        else -> ModernUi.Tone.NEUTRAL
    }

    companion object {
        private const val PREFS = "self_update_preferences"
        private const val KEY_AUTO_SILENT = "auto_silent_after_download"
    }
}
