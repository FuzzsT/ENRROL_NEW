package io.dpcaio.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.offline.OfflineMode
import io.dpcaio.offline.OfflineReadinessStatus
import io.dpcaio.offline.android.AndroidOfflineBundleStore
import io.dpcaio.offline.android.AndroidOfflineBundleReader
import io.dpcaio.offline.android.AndroidOfflinePackageInstaller
import io.dpcaio.offline.OfflineReadinessInput
import io.dpcaio.offline.OfflineReadinessPlanner
import java.io.File
import java.security.MessageDigest

class OfflineSetupActivity : Activity() {
    private lateinit var output: TextView
    private var mode: OfflineMode = OfflineMode.FULL_OFFLINE
    private var importedBundle: File? = null
    private var bundleInspection: io.dpcaio.offline.android.AndroidOfflineBundleInspection? = null
    private val bundleStore by lazy { AndroidOfflineBundleStore(this) }
    private val bundleReader by lazy { AndroidOfflineBundleReader(this) }
    private val packageInstaller by lazy { AndroidOfflinePackageInstaller(this) }
    private val deploymentStore by lazy { OfflineDeploymentStore(this) }
    private val coordinator by lazy { OfflineDeploymentCoordinator(deploymentStore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Full Offline Setup"
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        body.addView(TextView(this).apply { text = "FULL OFFLINE / OFFLINE THEN SYNC\nFinal state: OFFLINE_VERIFIED only after package/policy readback" })
        body.addView(Button(this).apply { text = "FULL OFFLINE"; setOnClickListener { mode = OfflineMode.FULL_OFFLINE; renderState("Mode: FULL OFFLINE") } })
        body.addView(Button(this).apply { text = "OFFLINE THEN SYNC"; setOnClickListener { mode = OfflineMode.OFFLINE_THEN_SYNC; renderState("Mode: OFFLINE THEN SYNC") } })
        body.addView(Button(this).apply { text = "Import offline bundle"; setOnClickListener { startActivityForResult(bundleStore.createImportIntent(), REQ_IMPORT) } })
        body.addView(Button(this).apply { text = "Preview deployment"; setOnClickListener { preview() } })
        body.addView(Button(this).apply { text = "Apply supported"; setOnClickListener { applySupported() } })
        body.addView(Button(this).apply { text = "Refresh state"; setOnClickListener { showPersistedState() } })
        output = TextView(this).apply { setTextIsSelectable(true) }
        body.addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(DpcUiShell.scroll(this, body))
        showPersistedState()
    }

    @Deprecated("legacy Activity result used to keep the app dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        runCatching { bundleStore.copyToPrivateStorage(uri) }
            .onSuccess { file ->
                importedBundle = file
                bundleInspection = bundleReader.inspect(file, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)
                val inspected = bundleInspection!!
                renderState(buildString {
                    appendLine("Bundle imported")
                    appendLine("path=${file.name}")
                    appendLine("sha256=${sha256(file)}")
                    appendLine("signatureVerified=${inspected.signatureVerified}")
                    appendLine("packageFilesVerified=${inspected.packagePlan.verifiedFiles.size}")
                    appendLine("packageIssues=${inspected.packagePlan.issues.size}")
                    appendLine("signingIdentityIssues=${inspected.signingIdentityIssues.size}")
                    appendLine("detail=${inspected.detail}")
                })
            }
            .onFailure { renderState("OFFLINE_BUNDLE_INVALID\n${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun preview() {
        val file = importedBundle
        val inspected = bundleInspection
        val manifest = inspected?.manifest
        if (file == null || !file.isFile || manifest == null) {
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
                availableCapabilities = capabilities
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
        if (file == null || inspected?.ready != true || manifest == null) {
            renderState("OFFLINE_BUNDLE_INVALID\nSigned manifest/package verification must pass before apply")
            return
        }
        coordinator.startFullOffline(manifest.bundleId)
        deploymentStore.save(
            OfflineDeploymentState(
                bundleId = manifest.bundleId,
                stage = io.dpcaio.offline.OfflineStage.BUNDLE_VERIFIED,
                syncPending = mode == OfflineMode.OFFLINE_THEN_SYNC,
                bundlePath = file.absolutePath
            )
        )
        if (manifest.packages.isEmpty()) {
            deploymentStore.save(deploymentStore.load()!!.copy(stage = io.dpcaio.offline.OfflineStage.PACKAGES_INSTALLED))
            sendBroadcast(Intent(this, OfflineRecoveryReceiver::class.java).setAction(OfflineInstallStatusReceiver.ACTION_PACKAGE_DONE))
            renderState("PACKAGES_INSTALLED\nNo package payload; policy application scheduled")
            return
        }
        runCatching {
            val plan = packageInstaller.stageBundle(file, manifest)
            deploymentStore.save(deploymentStore.load()!!.copy(stage = io.dpcaio.offline.OfflineStage.PACKAGES_STAGED, parentSessionId = plan.parentSessionId, bundlePath = file.absolutePath))
            val callback = PendingIntent.getBroadcast(
                this,
                4101,
                Intent(this, OfflineInstallStatusReceiver::class.java).setAction(OfflineInstallStatusReceiver.ACTION_INSTALL_RESULT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            packageInstaller.commit(plan, callback.intentSender)
            renderState(buildString {
                appendLine("PACKAGES_STAGED")
                appendLine("bundle=${manifest.bundleId}")
                appendLine("packages=${manifest.packages.size}")
                appendLine("atomic=${plan.atomic}")
                appendLine("parentSessionId=${plan.parentSessionId}")
                appendLine("Waiting for PackageInstaller readback callback")
            })
        }.onFailure { error ->
            deploymentStore.save(deploymentStore.load()!!.copy(stage = io.dpcaio.offline.OfflineStage.FAILED, lastError = error.message))
            renderState("OFFLINE_POLICY_PARTIAL\nPACKAGE_STAGE_FAILED: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun shizukuAvailable(): Boolean = runCatching { io.dpcaio.shizuku.AndroidShizukuRuntime().probe().let { it.binderAlive && it.permissionGranted } }.getOrDefault(false)

    private fun showPersistedState() {
        val state = deploymentStore.load()
        renderState(if (state == null) "No offline deployment\nFULL_OFFLINE_READY requires a verified bundle" else "bundle=${state.bundleId}\nstage=${state.stage}\nsyncPending=${state.syncPending}")
    }

    private fun renderState(text: String) { output.text = text }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    companion object { private const val REQ_IMPORT = 4100 }
}
