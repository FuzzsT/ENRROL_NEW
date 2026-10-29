package io.dpcaio.app

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import io.dpcaio.selfupdate.SelfUpdateInstallPolicy
import io.dpcaio.selfupdate.SelfUpdateManifest
import io.dpcaio.selfupdate.SelfUpdatePlanner
import io.dpcaio.selfupdate.SelfUpdateState
import io.dpcaio.selfupdate.UpdateChannel
import io.dpcaio.selfupdate.android.SelfUpdateClient
import io.dpcaio.selfupdate.android.SelfUpdateInstaller
import io.dpcaio.selfupdate.android.SelfUpdateStore
import io.dpcaio.selfupdate.android.SelfUpdateVerifier
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class SelfUpdateActivity : Activity() {
    private val planner = SelfUpdatePlanner()
    private val client = SelfUpdateClient()
    private val store by lazy { SelfUpdateStore(this) }
    private val verifier by lazy { SelfUpdateVerifier(this) }
    private val installer by lazy { SelfUpdateInstaller(this) }
    private val preferences by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var selectedChannel = UpdateChannel.STABLE
    private var selectedPolicy = SelfUpdateInstallPolicy.SILENT_IF_ALLOWED
    private var currentManifest: SelfUpdateManifest? = null
    private var state = SelfUpdateState.IDLE
    private var message = "Ready"
    private var progressPercent: Int? = null
    private var registered = false

    private val stateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val snapshot = SelfUpdateUiStateStore.read(this@SelfUpdateActivity)
            state = snapshot.state
            message = snapshot.message
            progressPercent = null
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Self Update"
        selectedChannel = runCatching {
            UpdateChannel.valueOf(preferences.getString(KEY_CHANNEL, UpdateChannel.STABLE.name) ?: UpdateChannel.STABLE.name)
        }.getOrDefault(UpdateChannel.STABLE)
        selectedPolicy = runCatching {
            SelfUpdateInstallPolicy.valueOf(
                preferences.getString(KEY_POLICY, SelfUpdateInstallPolicy.SILENT_IF_ALLOWED.name)
                    ?: SelfUpdateInstallPolicy.SILENT_IF_ALLOWED.name
            )
        }.getOrDefault(SelfUpdateInstallPolicy.SILENT_IF_ALLOWED)
        val snapshot = SelfUpdateUiStateStore.read(this)
        state = snapshot.state
        message = snapshot.message
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
        val snapshot = SelfUpdateUiStateStore.read(this)
        state = snapshot.state
        message = snapshot.message
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
        val body = ModernUi.page(this)
        val installed = installedVersion()
        val candidate = store.loadCandidate()
        val manifest = currentManifest ?: candidate?.manifest
        val silentAvailable = silentInstallLikelyAvailable()

        body.addView(
            ModernUi.hero(
                this,
                title = "Self Update",
                subtitle = "Verified updates for DPC-AIO using Android PackageInstaller. Silent install is attempted only when Android policy permits it.",
                eyebrow = "${selectedChannel.name} • ${state.name}",
            )
        )

        body.addView(ModernUi.section(this, "Version & status"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Installed", "${installed.first} (${installed.second})"))
                addView(
                    ModernUi.detailRow(
                        this@SelfUpdateActivity,
                        "Latest",
                        manifest?.let { "${it.versionName} (${it.versionCode})" } ?: "Not checked",
                    )
                )
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Channel", selectedChannel.name))
                addView(
                    ModernUi.detailRow(
                        this@SelfUpdateActivity,
                        "Silent install",
                        if (silentAvailable) "AVAILABLE • Device Owner" else "BEST EFFORT • Android may require confirmation",
                    )
                )
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Last successful check", lastCheckText()))
            }
        )
        body.addView(ModernUi.statusCard(this, state.name.replace('_', ' '), message, toneForState(state)))
        if (state in setOf(SelfUpdateState.DOWNLOADING, SelfUpdateState.VERIFYING, SelfUpdateState.INSTALLING)) {
            body.addView(
                ModernUi.progressCard(
                    this,
                    when (state) {
                        SelfUpdateState.DOWNLOADING -> "Downloading update"
                        SelfUpdateState.VERIFYING -> "Verifying package"
                        else -> "Installing update"
                    },
                    progressPercent,
                    message,
                )
            )
        }

        body.addView(ModernUi.section(this, "Update channel"))
        body.addView(
            ModernUi.card(this).apply {
                val row = LinearLayout(this@SelfUpdateActivity).apply { orientation = LinearLayout.HORIZONTAL }
                UpdateChannel.entries.forEach { channel ->
                    val pill = ModernUi.pill(
                        this@SelfUpdateActivity,
                        channel.name,
                        channel == selectedChannel,
                        if (channel == UpdateChannel.STABLE) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING,
                    ) {
                        selectedChannel = channel
                        currentManifest = null
                        preferences.edit().putString(KEY_CHANNEL, channel.name).apply()
                        setState(SelfUpdateState.IDLE, "Channel changed to ${channel.name.lowercase(Locale.ROOT)}")
                    }
                    row.addView(
                        pill,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (row.childCount > 0) marginStart = ModernUi.dp(this@SelfUpdateActivity, 8)
                        },
                    )
                }
                addView(row)
            }
        )

        body.addView(ModernUi.section(this, "Verified candidate"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Package", manifest?.packageName ?: "—"))
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "APK size", manifest?.apkSize?.let(::formatBytes) ?: "—"))
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "SHA-256", manifest?.apkSha256 ?: "—"))
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Signer", if (candidate?.verified == true) "VERIFIED" else "Not verified"))
                addView(ModernUi.detailRow(this@SelfUpdateActivity, "Cached APK", if (candidate?.apkFile?.isFile == true) candidate.apkFile.name else "None"))
            }
        )

        body.addView(ModernUi.section(this, "Install policy"))
        body.addView(
            ModernUi.card(this).apply {
                val row = LinearLayout(this@SelfUpdateActivity).apply { orientation = LinearLayout.HORIZONTAL }
                listOf(SelfUpdateInstallPolicy.ASK_ALWAYS, SelfUpdateInstallPolicy.SILENT_IF_ALLOWED).forEach { policy ->
                    val pill = ModernUi.pill(
                        this@SelfUpdateActivity,
                        if (policy == SelfUpdateInstallPolicy.ASK_ALWAYS) "Ask always" else "Silent if allowed",
                        policy == selectedPolicy,
                        if (policy == SelfUpdateInstallPolicy.SILENT_IF_ALLOWED) ModernUi.Tone.SUCCESS else ModernUi.Tone.NEUTRAL,
                    ) {
                        selectedPolicy = policy
                        preferences.edit().putString(KEY_POLICY, policy.name).apply()
                        render()
                    }
                    row.addView(
                        pill,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (row.childCount > 0) marginStart = ModernUi.dp(this@SelfUpdateActivity, 8)
                        },
                    )
                }
                addView(row)
                addView(ModernUi.spacer(this@SelfUpdateActivity, 8))
                addView(CheckBox(this@SelfUpdateActivity).apply {
                    text = "Automatically install a downloaded verified update when silent install is supported"
                    isChecked = preferences.getBoolean(KEY_AUTO_SILENT, false)
                    setOnCheckedChangeListener { _, checked ->
                        preferences.edit().putBoolean(KEY_AUTO_SILENT, checked).apply()
                    }
                })
            }
        )

        body.addView(ModernUi.section(this, "Actions"))
        val updateAvailable = currentManifest?.let {
            planner.evaluate(packageName, installed.second, selectedChannel, it).accepted
        } == true
        val verifiedCandidate = candidate?.verified == true && candidate.apkFile.isFile
        val notesAvailable = manifest?.releaseNotesUrl?.startsWith("https://", ignoreCase = true) == true

        body.addView(
            ModernUi.actionRow(
                this,
                ModernUi.action(this, "Check now", primary = true) { checkNow() },
                ModernUi.action(this, "Download & verify") { downloadAndVerify() }.apply { isEnabled = updateAvailable },
            )
        )
        body.addView(
            ModernUi.actionRow(
                this,
                ModernUi.action(this, "Install update", primary = true) { submitInstall(selectedPolicy) }.apply { isEnabled = verifiedCandidate },
                ModernUi.action(this, "Install silently") { submitInstall(SelfUpdateInstallPolicy.SILENT_IF_ALLOWED) }.apply { isEnabled = verifiedCandidate },
            )
        )
        body.addView(
            ModernUi.actionRow(
                this,
                ModernUi.action(this, "Release notes") { openReleaseNotes() }.apply { isEnabled = notesAvailable },
                ModernUi.action(this, "Clear downloaded update", destructive = true) { clearCandidate() }.apply { isEnabled = candidate != null },
            )
        )

        body.addView(
            ModernUi.statusCard(
                this,
                "Verification boundary",
                "HTTPS + version + SHA-256 + package + signer checks run before install. PackageInstaller remains authoritative; no root, Shizuku shell, ADB or automated confirmation bypass is used.",
                ModernUi.Tone.NEUTRAL,
            )
        )

        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun checkNow() {
        val channel = selectedChannel
        setState(SelfUpdateState.CHECKING, "Checking ${channel.name.lowercase(Locale.ROOT)} channel…")
        Thread {
            runCatching {
                val manifest = client.fetchManifest(manifestUrl(channel))
                val installed = installedVersion().second
                val decision = planner.evaluate(packageName, installed, channel, manifest)
                runOnUiThread {
                    currentManifest = manifest
                    preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    setState(decision.state, decision.reason.replace('_', ' '))
                }
            }.onFailure { error ->
                runOnUiThread { setState(SelfUpdateState.ERROR, "Update check failed: ${error.message ?: error.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun downloadAndVerify() {
        val manifest = currentManifest ?: return setState(SelfUpdateState.ERROR, "Check for an update first")
        val installedVersionCode = installedVersion().second
        val decision = planner.evaluate(packageName, installedVersionCode, selectedChannel, manifest)
        if (!decision.accepted) return setState(decision.state, decision.reason.replace('_', ' '))

        progressPercent = 0
        setState(SelfUpdateState.DOWNLOADING, "Downloading verified candidate…")
        Thread {
            val temp = store.newDownloadFile()
            runCatching {
                client.downloadApk(manifest.apkUrl, temp) { downloaded, total ->
                    val percent = total?.takeIf { it > 0L }?.let { ((downloaded * 100L) / it).toInt().coerceIn(0, 100) }
                    if (percent != null && percent != progressPercent) {
                        progressPercent = percent
                        runOnUiThread {
                            message = "${formatBytes(downloaded)}${total?.let { " / ${formatBytes(it)}" }.orEmpty()}"
                            render()
                        }
                    }
                }
                runOnUiThread {
                    progressPercent = null
                    setState(SelfUpdateState.VERIFYING, "Checking hash, package, version and signing identity…")
                }
                val result = verifier.verify(temp, manifest, packageName)
                if (!result.accepted) {
                    temp.delete()
                    throw IllegalStateException(result.reason)
                }
                store.promoteDownload(temp)
                store.saveCandidate(manifest, verified = true)
                runOnUiThread {
                    progressPercent = null
                    setState(SelfUpdateState.VERIFIED, "Verified ${manifest.versionName} (${manifest.versionCode})")
                    if (preferences.getBoolean(KEY_AUTO_SILENT, false)) {
                        if (silentInstallLikelyAvailable()) {
                            submitInstall(SelfUpdateInstallPolicy.AUTO_SILENT)
                        } else {
                            setState(SelfUpdateState.VERIFIED, "Verified; auto-silent skipped because Device Owner capability is not available")
                        }
                    }
                }
            }.onFailure { error ->
                temp.delete()
                runOnUiThread {
                    progressPercent = null
                    setState(SelfUpdateState.ERROR, "Verification failed: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }.start()
    }

    private fun submitInstall(policy: SelfUpdateInstallPolicy) {
        val candidate = store.loadCandidate()
            ?: return setState(SelfUpdateState.ERROR, "No verified update candidate")
        if (!candidate.verified || !candidate.apkFile.isFile) {
            return setState(SelfUpdateState.ERROR, "Candidate is not verified")
        }

        setState(SelfUpdateState.INSTALLING, "Submitting ${policy.name.lowercase(Locale.ROOT)} PackageInstaller session…")
        val callback = Intent(this, SelfUpdateStatusReceiver::class.java)
            .setAction(SelfUpdateStatusReceiver.ACTION_INSTALL_STATUS)
            .putExtra(SelfUpdateStatusReceiver.EXTRA_POLICY, policy.name)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val requestCode = (candidate.manifest.versionCode and 0x7fffffff).toInt()
        val sender = PendingIntent.getBroadcast(this, requestCode, callback, pendingFlags).intentSender

        Thread {
            val submission = installer.submit(candidate, policy, sender)
            if (!submission.submitted) {
                runOnUiThread { setState(SelfUpdateState.ERROR, submission.message) }
            }
        }.start()
    }

    private fun clearCandidate() {
        store.clearCandidate()
        currentManifest = null
        progressPercent = null
        setState(SelfUpdateState.IDLE, "Downloaded update cleared")
    }

    private fun openReleaseNotes() {
        val url = (currentManifest ?: store.loadCandidate()?.manifest)?.releaseNotesUrl
        if (url.isNullOrBlank() || !url.startsWith("https://", ignoreCase = true)) {
            setState(SelfUpdateState.ERROR, "Release notes URL unavailable")
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { setState(SelfUpdateState.ERROR, "Cannot open release notes") }
    }

    private fun setState(next: SelfUpdateState, detail: String) {
        state = next
        message = detail
        SelfUpdateUiStateStore.write(this, next, detail)
        render()
    }

    private fun installedVersion(): Pair<String, Long> {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return (info.versionName ?: "unknown") to code
    }

    private fun silentInstallLikelyAvailable(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching { dpm.isDeviceOwnerApp(packageName) }.getOrDefault(false)
    }

    private fun lastCheckText(): String {
        val value = preferences.getLong(KEY_LAST_CHECK, 0L)
        return if (value <= 0L) "Never" else DateFormat.getDateTimeInstance().format(Date(value))
    }

    private fun toneForState(value: SelfUpdateState): ModernUi.Tone = when (value) {
        SelfUpdateState.UP_TO_DATE, SelfUpdateState.VERIFIED, SelfUpdateState.UPDATED -> ModernUi.Tone.SUCCESS
        SelfUpdateState.ERROR -> ModernUi.Tone.DANGER
        SelfUpdateState.UPDATE_AVAILABLE, SelfUpdateState.WAITING_USER -> ModernUi.Tone.WARNING
        else -> ModernUi.Tone.NEUTRAL
    }

    private fun manifestUrl(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> STABLE_MANIFEST_URL
        UpdateChannel.CONTINUOUS -> CONTINUOUS_MANIFEST_URL
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        private const val PREFS = "self_update_preferences"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_POLICY = "install_policy"
        private const val KEY_AUTO_SILENT = "auto_silent_after_download"
        private const val KEY_LAST_CHECK = "last_successful_check"
        private const val STABLE_MANIFEST_URL =
            "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-stable/self-update.json"
        private const val CONTINUOUS_MANIFEST_URL =
            "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/self-update.json"
    }
}
