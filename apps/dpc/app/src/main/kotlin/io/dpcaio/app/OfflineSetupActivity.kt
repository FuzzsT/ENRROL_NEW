package io.dpcaio.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.offline.OfflineMode
import io.dpcaio.offline.OfflineReadinessInput
import io.dpcaio.offline.OfflineReadinessPlanner
import io.dpcaio.offline.OfflineReadinessStatus
import io.dpcaio.offline.OfflineStage
import io.dpcaio.offline.android.AndroidOfflineBundleReader
import io.dpcaio.offline.android.AndroidOfflineBundleStore
import io.dpcaio.offline.android.AndroidOfflinePackageInstaller
import java.io.File
import java.security.MessageDigest

class OfflineSetupActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var modeChip: TextView
    private var mode: OfflineMode = OfflineMode.FULL_OFFLINE
    private var importedBundle: File? = null
    private var bundleInspection: io.dpcaio.offline.android.AndroidOfflineBundleInspection? = null
    private var importGeneration = 0L
    private val bundleStore by lazy { AndroidOfflineBundleStore(this) }
    private val bundleReader by lazy { AndroidOfflineBundleReader(this) }
    private val deploymentStore by lazy { OfflineDeploymentStore(this) }
    private val coordinator by lazy { OfflineDeploymentCoordinator(deploymentStore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Full Offline Setup"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Full Offline Setup",
                subtitle = "Import a signed deployment bundle, preview readiness, then apply only supported operations.",
                eyebrow = "OFFLINE PROVISIONING",
            )
        )

        body.addView(ModernUi.section(this, "Deployment mode"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(
                    ModernUi.body(
                        this@OfflineSetupActivity,
                        "Final state becomes OFFLINE_VERIFIED only after package and policy readback succeeds.",
                    )
                )
                modeChip = ModernUi.chip(this@OfflineSetupActivity, "FULL OFFLINE", ModernUi.Tone.SUCCESS)
                addView(modeChip)
                addView(ModernUi.spacer(this@OfflineSetupActivity, 10))
                addView(
                    ModernUi.actionRow(
                        this@OfflineSetupActivity,
                        ModernUi.action(this@OfflineSetupActivity, "FULL OFFLINE", primary = true) {
                            mode = OfflineMode.FULL_OFFLINE
                            modeChip.text = "FULL OFFLINE"
                            renderState("Mode: FULL OFFLINE")
                        },
                        ModernUi.action(this@OfflineSetupActivity, "OFFLINE THEN SYNC") {
                            mode = OfflineMode.OFFLINE_THEN_SYNC
                            modeChip.text = "OFFLINE THEN SYNC"
                            renderState("Mode: OFFLINE THEN SYNC")
                        },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Bundle"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@OfflineSetupActivity,
                        "Bundle signatures, package files and signer identity are verified before deployment can proceed.",
                    )
                )
                addView(ModernUi.spacer(this@OfflineSetupActivity, 8))
                addView(
                    ModernUi.action(this@OfflineSetupActivity, "Import offline bundle", primary = true) {
                        startActivityForResult(bundleStore.createImportIntent(), REQ_IMPORT)
                    }
                )
            }
        )

        body.addView(ModernUi.section(this, "Deployment"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.actionRow(
                        this@OfflineSetupActivity,
                        ModernUi.action(this@OfflineSetupActivity, "Preview deployment") { preview() },
                        ModernUi.action(this@OfflineSetupActivity, "Apply supported", primary = true) { applySupported() },
                    )
                )
                addView(
                    ModernUi.action(this@OfflineSetupActivity, "Refresh state") { showPersistedState() }
                )
            }
        )

        body.addView(ModernUi.section(this, "Live result"))
        output = ModernUi.outputCard(this, "Loading offline deployment state...")
        body.addView(output)

        setContentView(ModernUi.scrollPage(this, body))
        showPersistedState()
    }

    @Deprecated("legacy Activity result used to keep the app dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK) return
        val sourceUri: Uri = data?.data ?: return
        importOfflineBundle(sourceUri)
    }

    private fun importOfflineBundle(uri: Uri) {
        if (deploymentOwnsBundle()) {
            renderState("Offline deployment is still using the current bundle")
            return
        }
        if (!bundleIoInProgress.compareAndSet(false, true)) {
            renderState("Offline bundle I/O already in progress")
            return
        }
        val sourceUri = uri
        val generation = ++importGeneration
        importedBundle = null
        bundleInspection = null
        renderState("Importing offline bundle...")
        val worker = Thread {
            val result = try {
                runCatching {
                    val file = bundleStore.copyToPrivateStorage(sourceUri)
                    val inspected = bundleReader.inspect(file, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)
                    OfflineBundleImportResult(file, inspected, sha256(file))
                }
            } finally {
                bundleIoInProgress.set(false)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != importGeneration) return@runOnUiThread
                result.onSuccess { imported ->
                    importedBundle = imported.file
                    bundleInspection = imported.inspection
                    renderState(buildString {
                        appendLine("Bundle imported")
                        appendLine("path=${imported.file.name}")
                        appendLine("sha256=${imported.sha256}")
                        appendLine("signatureVerified=${imported.inspection.signatureVerified}")
                        appendLine("packageFilesVerified=${imported.inspection.packagePlan.verifiedFiles.size}")
                        appendLine("packageIssues=${imported.inspection.packagePlan.issues.size}")
                        appendLine("signingIdentityIssues=${imported.inspection.signingIdentityIssues.size}")
                        appendLine("detail=${imported.inspection.detail}")
                    })
                }.onFailure { error ->
                    renderState("OFFLINE_BUNDLE_INVALID\n${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
        runCatching { worker.start() }.onFailure { error ->
            bundleIoInProgress.set(false)
            if (!isFinishing && !isDestroyed) {
                renderState("OFFLINE_BUNDLE_INVALID\n${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun preview() {
        val file = importedBundle
        val inspected = bundleInspection
        val manifest = inspected?.manifest
        if (file == null || !file.isFile || inspected == null || manifest == null) {
            renderState("${OfflineReadinessStatus.OFFLINE_BUNDLE_INVALID}\nImport and verify an offline bundle first")
            return
        }
        val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val provisioningMode = when {
            dpm.isDeviceOwnerApp(packageName) -> "FULLY_MANAGED"
            dpm.isProfileOwnerApp(packageName) -> "MANAGED_PROFILE"
            else -> "UNMANAGED"
        }
        val capabilities = buildSet {
            if (dpm.isDeviceOwnerApp(packageName) || dpm.isProfileOwnerApp(packageName)) add("PERMISSION_CONTROL")
            if (shizukuAvailable()) add("COMPONENT_CONTROL")
            if (dpm.isDeviceOwnerApp(packageName)) add("PACKAGE_INSTALL")
        }
        val readiness = OfflineReadinessPlanner().evaluate(
            manifest,
            OfflineReadinessInput(
                signatureVerified = inspected.signatureVerified && inspected.signingIdentityIssues.isEmpty() && inspected.packagePlan.ready,
                schemaSupported = manifest.schemaVersion == 1,
                currentAndroidApi = android.os.Build.VERSION.SDK_INT,
                provisioningMode = provisioningMode,
                currentDpcVersion = BuildConfig.VERSION_NAME,
                availablePackageFiles = inspected.packagePlan.verifiedFiles.toSet(),
                availableCapabilities = capabilities,
            )
        )
        renderState(buildString {
            appendLine("${readiness.status}")
            appendLine("Mode: ${if (mode == OfflineMode.FULL_OFFLINE) "FULL OFFLINE" else "OFFLINE THEN SYNC"}")
            appendLine("Bundle: ${manifest.bundleId}")
            appendLine("Packages: ${manifest.packages.size}")
            appendLine("Verified files: ${inspected.packagePlan.verifiedFiles.size}")
            appendLine("Capabilities: ${capabilities.sorted()}")
            readiness.details.forEach(::appendLine)
            appendLine("Preview deployment")
            appendLine("No backend calls are made in FULL_OFFLINE")
        })
    }

    private fun applySupported() {
        val file = importedBundle
        val inspected = bundleInspection
        val manifest = inspected?.manifest
        if (file == null || inspected == null || !inspected.ready || manifest == null) {
            renderState("OFFLINE_BUNDLE_INVALID\nSigned manifest/package verification must pass before apply")
            return
        }
        if (deploymentOwnsBundle()) {
            renderState("Offline deployment is already using the current bundle")
            return
        }
        val syncPending = mode == OfflineMode.OFFLINE_THEN_SYNC
        if (manifest.packages.isEmpty()) {
            coordinator.startFullOffline(manifest.bundleId)
            deploymentStore.save(
                OfflineDeploymentState(
                    bundleId = manifest.bundleId,
                    stage = OfflineStage.PACKAGES_INSTALLED,
                    syncPending = syncPending,
                    bundlePath = file.absolutePath,
                )
            )
            sendBroadcast(
                Intent(this, OfflineRecoveryReceiver::class.java)
                    .setAction(OfflineInstallStatusReceiver.ACTION_PACKAGE_DONE)
            )
            renderState("PACKAGES_INSTALLED\nNo package payload; policy application scheduled")
            return
        }
        stageAndCommitBundle(file, manifest, syncPending)
    }

    private fun stageAndCommitBundle(
        file: File,
        manifest: io.dpcaio.offline.OfflineBundleManifest,
        syncPending: Boolean,
    ) {
        if (!bundleIoInProgress.compareAndSet(false, true)) {
            renderState("Offline bundle I/O already in progress")
            return
        }
        val appContext = applicationContext
        renderState("Staging offline package payload...")
        val worker = Thread {
            val packageInstaller = AndroidOfflinePackageInstaller(appContext)
            val workerStore = OfflineDeploymentStore(appContext)
            val workerCoordinator = OfflineDeploymentCoordinator(workerStore)
            val result = try {
                runCatching {
                    workerCoordinator.startFullOffline(manifest.bundleId)
                    workerStore.save(
                        OfflineDeploymentState(
                            bundleId = manifest.bundleId,
                            stage = OfflineStage.BUNDLE_VERIFIED,
                            syncPending = syncPending,
                            bundlePath = file.absolutePath,
                        )
                    )
                    val plan = packageInstaller.stageBundle(file, manifest)
                    workerStore.save(
                        workerStore.load()!!.copy(
                            stage = OfflineStage.PACKAGES_STAGED,
                            parentSessionId = plan.parentSessionId,
                            bundlePath = file.absolutePath,
                        )
                    )
                    val callback = PendingIntent.getBroadcast(
                        appContext,
                        4101,
                        Intent(appContext, OfflineInstallStatusReceiver::class.java)
                            .setAction(OfflineInstallStatusReceiver.ACTION_INSTALL_RESULT),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    packageInstaller.commit(plan, callback.intentSender)
                    plan
                }.onFailure { error ->
                    workerStore.load()?.let { state ->
                        workerStore.save(
                            state.copy(
                                stage = OfflineStage.FAILED,
                                lastError = error.message,
                            )
                        )
                    }
                }
            } finally {
                bundleIoInProgress.set(false)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    renderState(buildString {
                        appendLine("PACKAGES_STAGED")
                        appendLine("bundle=${manifest.bundleId}")
                        appendLine("packages=${manifest.packages.size}")
                        appendLine("atomic=${plan.atomic}")
                        appendLine("parentSessionId=${plan.parentSessionId}")
                        appendLine("Waiting for PackageInstaller readback callback")
                    })
                }.onFailure { error ->
                    renderState("OFFLINE_POLICY_PARTIAL\nPACKAGE_STAGE_FAILED: ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
        runCatching { worker.start() }.onFailure { error ->
            bundleIoInProgress.set(false)
            if (!isFinishing && !isDestroyed) {
                renderState("OFFLINE_POLICY_PARTIAL\nPACKAGE_STAGE_FAILED: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun shizukuAvailable(): Boolean = runCatching {
        io.dpcaio.shizuku.AndroidShizukuRuntime().probe().let { it.binderAlive && it.permissionGranted }
    }.getOrDefault(false)

    private fun deploymentOwnsBundle(): Boolean = when (deploymentStore.load()?.stage) {
        OfflineStage.PACKAGES_STAGED,
        OfflineStage.PACKAGES_INSTALLED,
        OfflineStage.PERMISSIONS_APPLIED,
        OfflineStage.COMPONENTS_APPLIED,
        OfflineStage.POLICIES_APPLIED,
        OfflineStage.READBACK_VERIFIED -> true
        else -> false
    }

    private fun showPersistedState() {
        val state = deploymentStore.load()
        renderState(
            if (state == null) {
                "No offline deployment\nFULL_OFFLINE_READY requires a verified bundle"
            } else {
                "bundle=${state.bundleId}\nstage=${state.stage}\nsyncPending=${state.syncPending}"
            }
        )
    }

    private fun renderState(text: String) {
        output.text = text
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class OfflineBundleImportResult(
        val file: File,
        val inspection: io.dpcaio.offline.android.AndroidOfflineBundleInspection,
        val sha256: String,
    )

    companion object {
        private const val REQ_IMPORT = 4100
        private val bundleIoInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}
