package io.dpcaio.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import io.dpcaio.installer.PackageSource
import io.dpcaio.installer.android.AndroidApkPlusStager
import io.dpcaio.installer.android.AndroidPackageInstallerAdapter
import io.dpcaio.installer.android.InstallSessionSpec
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class AppPackageManagerActivity : Activity() {
    private data class InstalledApp(
        val label: String,
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
    ) {
        override fun toString(): String = "$label • $packageName • ${versionName ?: versionCode}"
    }

    private data class InstallSubmission(
        val summary: String,
        val sessionId: Int? = null,
        val packageName: String? = null,
        val versionCode: Long? = null,
    )

    private enum class Mode { INSTALLER, UPDATER }

    private lateinit var status: TextView
    private lateinit var queueView: TextView
    private lateinit var historyView: TextView
    private lateinit var installedSpinner: Spinner
    private lateinit var queueStore: InstallQueueStore
    private lateinit var queueCoordinator: InstallQueueCoordinator
    private var installedApps: List<InstalledApp> = emptyList()
    private var receiverRegistered = false

    private val mode: Mode by lazy {
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_UPDATER -> Mode.UPDATER
            else -> Mode.INSTALLER
        }
    }

    private val queueChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderQueueAndHistory()
            continueQueueIfReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = if (mode == Mode.UPDATER) "App Updater" else "APK/APKS Installer"
        queueStore = InstallQueueStore(this)
        queueCoordinator = InstallQueueCoordinator(queueStore)
        if (savedInstanceState == null) queueCoordinator.recoverInterrupted()

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = title.toString(),
                subtitle = if (mode == Mode.UPDATER) {
                    "Choose an installed app, then queue matching APK or APKS update packages."
                } else {
                    "Install local APK or verified APKS packages sequentially through Android PackageInstaller."
                },
                eyebrow = "PACKAGE INSTALLER",
            )
        )

        if (mode == Mode.UPDATER) {
            body.addView(ModernUi.section(this, "Installed application"))
            body.addView(
                ModernUi.card(this).apply {
                    installedSpinner = Spinner(this@AppPackageManagerActivity)
                    addView(installedSpinner)
                    addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                    addView(ModernUi.action(this@AppPackageManagerActivity, "Refresh installed apps") { loadInstalledPackages() })
                }
            )
        }

        body.addView(ModernUi.section(this, "Package file"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@AppPackageManagerActivity,
                        "APK is parsed before staging. APKS archives are extracted only after path, size, package-name and signer consistency checks pass.",
                    )
                )
                addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                addView(ModernUi.action(this@AppPackageManagerActivity, "Add to queue", primary = true) {
                    openPackagePicker(allowMultiple = false)
                })
                addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                addView(ModernUi.action(this@AppPackageManagerActivity, "Add multiple") {
                    openPackagePicker(allowMultiple = true)
                })
            }
        )

        body.addView(ModernUi.section(this, "Install Queue"))
        body.addView(
            ModernUi.card(this).apply {
                queueView = ModernUi.body(this@AppPackageManagerActivity, "Queue is empty")
                addView(queueView)
                addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                addView(ModernUi.action(this@AppPackageManagerActivity, "Start / Resume queue", primary = true) { continueQueueIfReady() })
                addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                addView(ModernUi.action(this@AppPackageManagerActivity, "Retry failed") {
                    queueCoordinator.retryFailed()
                    renderQueueAndHistory()
                    continueQueueIfReady()
                })
                addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                addView(ModernUi.action(this@AppPackageManagerActivity, "Clear completed") {
                    queueCoordinator.clearCompleted()
                    renderQueueAndHistory()
                })
            }
        )

        body.addView(ModernUi.section(this, "History"))
        body.addView(
            ModernUi.card(this).apply {
                historyView = ModernUi.body(this@AppPackageManagerActivity, "No install history")
                addView(historyView)
            }
        )

        if (Build.VERSION.SDK_INT >= 26) {
            body.addView(ModernUi.section(this, "Installer permission"))
            body.addView(
                ModernUi.card(this).apply {
                    addView(ModernUi.body(this@AppPackageManagerActivity, "Unknown-app install access is controlled by Android. Device-owner or managed-device behavior still follows platform policy."))
                    addView(ModernUi.spacer(this@AppPackageManagerActivity, 8))
                    addView(ModernUi.action(this@AppPackageManagerActivity, "Open install permission") {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:$packageName")))
                    })
                }
            )
        }

        body.addView(ModernUi.section(this, "Status"))
        status = ModernUi.outputCard(this, "Ready")
        body.addView(status)

        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@AppPackageManagerActivity, "Verification boundary"))
                addView(ModernUi.body(this@AppPackageManagerActivity, "No shell/root install path is used. PackageInstaller remains the authority and user confirmation is requested where Android requires it."))
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        if (mode == Mode.UPDATER) loadInstalledPackages()
        renderLastResult()
        renderQueueAndHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            renderLastResult()
            renderQueueAndHistory()
            continueQueueIfReady()
        }
        if (!receiverRegistered) {
            val filter = IntentFilter(AppInstallStatusReceiver.ACTION_QUEUE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(queueChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(queueChangedReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(queueChangedReceiver) }
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun loadInstalledPackages() {
        installedApps = runCatching {
            packageManager.getInstalledPackages(PackageManager.MATCH_ALL)
                .asSequence()
                .filter { it.packageName != packageName }
                .map { info -> InstalledApp(
                    label = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: info.packageName,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = versionCode(info),
                ) }
                .sortedWith(compareBy<InstalledApp>({ it.label.lowercase(Locale.ROOT) }, { it.packageName }))
                .toList()
        }.getOrElse { emptyList() }
        installedSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, installedApps)
        status.text = "Installed apps: ${installedApps.size}"
    }

    private fun openPackagePicker(allowMultiple: Boolean) {
        val userActionMode = if (Build.VERSION.SDK_INT >= 31) PackageInstaller.SessionParams.USER_ACTION_REQUIRED else -1
        status.text = "Opening package picker… userActionMode=$userActionMode"
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream"))
        }
        startActivityForResult(picker, REQ_PACKAGE)
    }

    @Deprecated("legacy Activity result keeps the app dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PACKAGE || resultCode != RESULT_OK || data == null) return
        val uris = buildList {
            data.data?.let(::add)
            val clips = data.clipData
            if (clips != null) for (i in 0 until clips.itemCount) add(clips.getItemAt(i).uri)
        }.distinct()
        if (uris.isEmpty()) return
        val selectedTarget = if (mode == Mode.UPDATER) installedApps.getOrNull(installedSpinner.selectedItemPosition) else null
        if (mode == Mode.UPDATER && selectedTarget == null) {
            status.text = "Select an installed application first"
            return
        }
        val items = uris.map { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            InstallQueueItem(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                displayName = displayName(uri),
                updaterMode = mode == Mode.UPDATER,
                targetPackage = selectedTarget?.packageName,
            )
        }
        queueCoordinator.enqueue(items)
        status.text = "Added ${items.size} item(s) to queue"
        renderQueueAndHistory()
    }

    private fun continueQueueIfReady() {
        if (!::queueStore.isInitialized) return
        val activeStates = setOf(InstallQueueState.PREFLIGHT, InstallQueueState.INSTALLING, InstallQueueState.WAITING_USER)
        if (queueStore.loadQueue().any { it.state in activeStates }) return
        val next = queueCoordinator.startOrResume() ?: return
        processQueueItem(next)
    }

    private fun processQueueItem(item: InstallQueueItem) {
        val target = item.targetPackage?.let { pkg -> installedApps.firstOrNull { it.packageName == pkg } ?: installedApp(pkg) }
        status.text = "Inspecting queued package: ${item.displayName}"
        Thread {
            val submission = runCatching {
                val uri = Uri.parse(item.uri)
                val imported = copyUriToCache(uri)
                try {
                    if (imported.name.lowercase(Locale.ROOT).endsWith(".apks")) installApksArchive(imported, target, item.id)
                    else if (imported.name.lowercase(Locale.ROOT).endsWith(".apk")) installSingleApk(imported, target, item.id)
                    else InstallSubmission("Unsupported file type. Choose .apk or .apks")
                } finally {
                    imported.delete()
                }
            }.getOrElse { error -> InstallSubmission("FAILED: ${error.javaClass.simpleName}: ${error.message}") }

            if (submission.sessionId != null) {
                queueCoordinator.markInstalling(item.id, submission.sessionId, submission.packageName, submission.versionCode)
            } else {
                queueCoordinator.failPreflight(item.id, submission.summary)
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    status.text = submission.summary
                    renderQueueAndHistory()
                    if (submission.sessionId == null) continueQueueIfReady()
                }
            }
        }.start()
    }

    private fun installSingleApk(file: File, target: InstalledApp?, queueItemId: String): InstallSubmission {
        val archiveInfo = getPackageArchiveInfo(file) ?: return InstallSubmission("APK_PARSE_FAILED")
        val preflight = verifyTargetAndSigner(archiveInfo, target)
        if (preflight != null) return InstallSubmission(preflight)

        val adapter = AndroidPackageInstallerAdapter(this)
        val session = adapter.createSession(InstallSessionSpec(archiveInfo.packageName, packageName, PackageSource.LOCAL_FILE, true))
        val sessionId = session.value ?: return InstallSubmission("SESSION_CREATE_FAILED: ${session.status}: ${session.message}")
        val staged = file.inputStream().use { adapter.stageSingleApk(sessionId, it, file.length()) }
        if (!staged.isSuccess) {
            adapter.abandon(sessionId)
            return InstallSubmission("STAGE_FAILED: ${staged.status}: ${staged.message}")
        }
        val committed = adapter.commit(sessionId, statusIntentSender(sessionId, queueItemId))
        if (!committed.isSuccess) {
            adapter.abandon(sessionId)
            return InstallSubmission("COMMIT_FAILED: ${committed.status}: ${committed.message}")
        }
        val version = versionCode(archiveInfo)
        return InstallSubmission("Install submitted\npackage=${archiveInfo.packageName}\nversion=$version\nbytes=${staged.value}\nAndroid may request confirmation.", sessionId, archiveInfo.packageName, version)
    }

    private fun installApksArchive(file: File, target: InstalledApp?, queueItemId: String): InstallSubmission {
        val stagedArchive = AndroidApkPlusStager(this).stage(file)
        if (!stagedArchive.accepted) return InstallSubmission("APKS_REJECTED: ${stagedArchive.detail}\n${stagedArchive.rejections.joinToString()}")
        val stagedFiles = stagedArchive.stagedFiles.map { it.file }
        val base = stagedFiles.firstOrNull { it.name == "base.apk" } ?: stagedFiles.firstOrNull() ?: return InstallSubmission("APKS_BASE_MISSING")
        try {
            val archiveInfo = getPackageArchiveInfo(base) ?: return InstallSubmission("APKS_BASE_PARSE_FAILED")
            val preflight = verifyTargetAndSigner(archiveInfo, target)
            if (preflight != null) return InstallSubmission(preflight)

            val adapter = AndroidPackageInstallerAdapter(this)
            val session = adapter.createSession(InstallSessionSpec(archiveInfo.packageName, packageName, PackageSource.LOCAL_FILE, true))
            val sessionId = session.value ?: return InstallSubmission("SESSION_CREATE_FAILED: ${session.status}: ${session.message}")
            val staged = adapter.stageApkSet(sessionId, stagedFiles)
            if (!staged.isSuccess) {
                adapter.abandon(sessionId)
                return InstallSubmission("SPLIT_STAGE_FAILED: ${staged.status}: ${staged.message}")
            }
            val committed = adapter.commit(sessionId, statusIntentSender(sessionId, queueItemId))
            if (!committed.isSuccess) {
                adapter.abandon(sessionId)
                return InstallSubmission("COMMIT_FAILED: ${committed.status}: ${committed.message}")
            }
            val version = versionCode(archiveInfo)
            return InstallSubmission("APKS install submitted\npackage=${archiveInfo.packageName}\nversion=$version\nsplits=${stagedFiles.size}\nbytes=${staged.value}\nAndroid may request confirmation.", sessionId, archiveInfo.packageName, version)
        } finally {
            stagedFiles.firstOrNull()?.parentFile?.deleteRecursively()
        }
    }

    private fun installedApp(targetPackage: String): InstalledApp? {
        val info = installedPackageInfo(targetPackage) ?: return null
        return InstalledApp(
            label = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: targetPackage,
            packageName = targetPackage,
            versionName = info.versionName,
            versionCode = versionCode(info),
        )
    }

    private fun verifyTargetAndSigner(incoming: PackageInfo, target: InstalledApp?): String? {
        if (target != null && incoming.packageName != target.packageName) return "PACKAGE_MISMATCH\nselected=${target.packageName}\nincoming=${incoming.packageName}"
        val installed = installedPackageInfo(incoming.packageName) ?: return null
        val incomingVersion = versionCode(incoming)
        val installedVersion = versionCode(installed)
        if (incomingVersion < installedVersion) return "DOWNGRADE_BLOCKED\ninstalled=$installedVersion\nincoming=$incomingVersion"
        val installedCurrent = currentSignerDigests(installed)
        val incomingLineage = signerLineageDigests(incoming)
        if (installedCurrent.isEmpty() || incomingLineage.isEmpty() || !installedCurrent.all { it in incomingLineage }) return "SIGNER_MISMATCH\nUpdate must continue the installed app's signing identity."
        return null
    }

    private fun installedPackageInfo(targetPackage: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) packageManager.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(targetPackage, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    }.getOrNull()

    private fun getPackageArchiveInfo(file: File): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    private fun currentSignerDigests(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }

    private fun signerLineageDigests(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        return (signing.apkContentsSigners.orEmpty().asSequence() + signing.signingCertificateHistory.orEmpty().asSequence()).mapTo(linkedSetOf()) { digest(it.toByteArray()) }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

    private fun statusIntentSender(sessionId: Int, queueItemId: String) = PendingIntent.getBroadcast(
        this,
        sessionId,
        Intent(this, AppInstallStatusReceiver::class.java)
            .setAction(AppInstallStatusReceiver.ACTION_INSTALL_RESULT)
            .putExtra(AppInstallStatusReceiver.QUEUE_ITEM_ID, queueItemId)
            .putExtra(AppInstallStatusReceiver.SESSION_ID, sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    ).intentSender

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "package.apk"

    private fun copyUriToCache(uri: Uri): File {
        val safeName = displayName(uri).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(cacheDir, "manual-installer/${System.nanoTime()}-$safeName")
        target.parentFile?.mkdirs()
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun renderQueueAndHistory() {
        if (!::queueView.isInitialized) return
        val queue = queueStore.loadQueue()
        queueView.text = if (queue.isEmpty()) "Queue is empty" else queue.joinToString("\n") {
            "${it.state} • ${it.displayName}${it.resolvedPackage?.let { pkg -> " • $pkg" } ?: ""}${it.detail?.let { detail -> "\n  $detail" } ?: ""}"
        }
        val history = queueStore.loadHistory()
        historyView.text = if (history.isEmpty()) "No install history" else history.take(20).joinToString("\n") {
            "${it.result} • ${it.displayName}${it.packageName?.let { pkg -> " • $pkg" } ?: ""}"
        }
    }

    private fun renderLastResult() {
        val prefs = getSharedPreferences(AppInstallStatusReceiver.PREFS, MODE_PRIVATE)
        val text = prefs.getString(AppInstallStatusReceiver.KEY_SUMMARY, null) ?: return
        status.text = text
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_INSTALLER = "installer"
        const val MODE_UPDATER = "updater"
        private const val REQ_PACKAGE = 4101
    }
}
