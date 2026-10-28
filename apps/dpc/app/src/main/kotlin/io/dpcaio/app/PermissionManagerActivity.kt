package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.permission.PermissionBatchChange
import io.dpcaio.permission.PermissionBatchPlan2
import io.dpcaio.permission.PermissionBatchTransaction
import io.dpcaio.permission.PermissionCatalogClassifier
import io.dpcaio.permission.PermissionCatalogEntry
import io.dpcaio.permission.PermissionControlRoute
import io.dpcaio.permission.PermissionProtection
import io.dpcaio.permission.PermissionSeedQueries
import io.dpcaio.permission.android.AndroidPermissionCatalog
import io.dpcaio.permission.android.AndroidPermissionGrantCoordinator
import io.dpcaio.permission.android.AndroidPermissionManagerGateway
import io.dpcaio.permission.android.GlobalRuntimePermissionPolicy
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.shizuku.ShizukuUserServiceClient

class PermissionManagerActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var query: EditText
    private lateinit var targetPackage: EditText
    private lateinit var targetUser: EditText
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val admin by lazy { ComponentName(this, AioDeviceAdminReceiver::class.java) }
    private val manager by lazy { AndroidPermissionManagerGateway(this, admin) }
    private val batchTransaction = PermissionBatchTransaction()
    private var pendingBatchChanges: List<PermissionBatchChange> = emptyList()
    private var pendingBatchPlan: PermissionBatchPlan2? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Permission Manager 2.0"

        val body = LinearLayout(this)
        body.addView(ModernUi.hero(
            this,
            title = "Permission Manager 2.0",
            subtitle = "Inspect actual state, DPC policy, AppOps and execution route from one surface.",
            eyebrow = "Security control",
        ))

        body.addView(ModernUi.section(this, "Target"))
        val targetCard = ModernUi.card(this, emphasized = true)
        targetPackage = ModernUi.field(this, "Target package, e.g. com.example.app")
        targetUser = ModernUi.field(this, "Target user ID", InputType.TYPE_CLASS_NUMBER).apply {
            setText(AndroidUserId.fromUid(Process.myUid()).toString())
        }
        query = ModernUi.field(this, "Permission / group / filter", InputType.TYPE_CLASS_TEXT)
        targetCard.addView(targetPackage)
        targetCard.addView(targetUser)
        targetCard.addView(query)
        targetCard.addView(row(
            action("Scan app permissions") { scanAppPermissions(false) },
            action("Show only anomalies") { scanAppPermissions(true) },
        ))
        body.addView(targetCard)

        body.addView(ModernUi.section(this, "Permission state"))
        body.addView(ModernUi.card(this).apply {
            addView(ModernUi.body(this@PermissionManagerActivity, "Apply an exact permission state using the best supported route."))
            addView(ModernUi.spacer(this@PermissionManagerActivity, 8))
            addView(row(
                action("Grant") { mutateSelected(ManagedPermissionState.GRANTED) },
                action("Deny", destructive = true) { mutateSelected(ManagedPermissionState.DENIED) },
                action("Default") { mutateSelected(ManagedPermissionState.DEFAULT) },
            ))
        })

        body.addView(ModernUi.section(this, "Global runtime policy"))
        body.addView(ModernUi.card(this).apply {
            addView(ModernUi.body(this@PermissionManagerActivity, "Global policy for future runtime requests"))
            addView(ModernUi.spacer(this@PermissionManagerActivity, 8))
            addView(row(
                action("Prompt") { setGlobalPolicy(GlobalRuntimePermissionPolicy.PROMPT) },
                action("Auto Grant") { setGlobalPolicy(GlobalRuntimePermissionPolicy.AUTO_GRANT) },
                action("Auto Deny", destructive = true) { setGlobalPolicy(GlobalRuntimePermissionPolicy.AUTO_DENY) },
            ))
        })

        body.addView(ModernUi.section(this, "Batch Preview"))
        body.addView(ModernUi.card(this).apply {
            addView(ModernUi.body(this@PermissionManagerActivity, "Preview supported and skipped changes before applying them."))
            addView(ModernUi.spacer(this@PermissionManagerActivity, 8))
            addView(row(
                action("Batch Grant") { batchPreview(ManagedPermissionState.GRANTED) },
                action("Batch Deny", destructive = true) { batchPreview(ManagedPermissionState.DENIED) },
                action("Batch Default") { batchPreview(ManagedPermissionState.DEFAULT) },
            ))
            addView(row(
                action("Apply supported") { applyPendingBatch() },
                action("Restore previous DPC states") { restorePreviousDpcStates() },
            ))
        })

        body.addView(ModernUi.section(this, "Research tools"))
        body.addView(ModernUi.card(this).apply {
            addView(action("Scan device permissions") { scanCatalog() })
            addView(ModernUi.spacer(this@PermissionManagerActivity, 7))
            addView(action("Extended scan via Shizuku") { scanShizuku() })
            addView(ModernUi.spacer(this@PermissionManagerActivity, 7))
            addView(action("AUTO: requested permissions") { autoRequestedPermissions() })
            addView(ModernUi.spacer(this@PermissionManagerActivity, 7))
            addView(action("System permission/sysconfig XML index") { scanSystemConfigs() })
        })

        body.addView(ModernUi.section(this, "Live result"))
        output = TextView(this).apply { text = "Ready." }
        body.addView(ModernUi.outputCard(this, output))

        setContentView(ModernUi.page(this, body))
        shizuku.bind()
    }

    private fun row(vararg buttons: Button): LinearLayout = ModernUi.actionRow(this, *buttons)

    private fun action(label: String, destructive: Boolean = false, action: () -> Unit): Button =
        ModernUi.action(this, label, destructive, action)

    private fun postUiIfAlive(block: () -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            block()
        }
    }

    private fun targetUserId(): Int = targetUser.text.toString().trim().toIntOrNull() ?: AndroidUserId.fromUid(Process.myUid())

    @Suppress("DEPRECATION")
    private fun requestedPermissions(packageName: String): List<String> = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
    }.getOrElse { emptyList() }

    private fun catalogEntry(permission: String): PermissionCatalogEntry? = AndroidPermissionCatalog(this).scan().permissions.firstOrNull { it.name == permission }

    private fun scanAppPermissions(anomaliesOnly: Boolean) {
        val pkg = targetPackage.text.toString().trim()
        val q = query.text.toString().trim().lowercase()
        val targetUserId = targetUserId()
        if (pkg.isBlank()) { output.text = "Target package required"; return }
        output.text = "Scanning $pkg..."
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan()
            val byName = snapshot.permissions.associateBy { it.name }
            val shizukuAvailable = shizuku.identity() != null
            val rows = requestedPermissions(pkg).map { permission ->
                val entry = byName[permission]
                manager.inspectPermission(
                    packageName = pkg,
                    permission = permission,
                    targetUserId = targetUserId,
                    group = entry?.group,
                    protection = entry?.protection ?: PermissionProtection.UNKNOWN,
                    shizukuAvailable = shizukuAvailable,
                    userActionAvailable = entry?.protection == PermissionProtection.SPECIAL_ACCESS
                )
            }.filter { record ->
                val matches = q.isBlank() || record.permission.lowercase().contains(q) || record.group?.lowercase()?.contains(q) == true || record.dpcState.name.lowercase().contains(q)
                val anomaly = record.dpcState == ManagedPermissionState.GRANTED && !record.actualGranted ||
                    (record.actualGranted && record.appOpState?.name == "IGNORED")
                matches && (!anomaliesOnly || anomaly)
            }
            val text = buildString {
                appendLine("Permission Manager 2.0")
                appendLine("Package: $pkg")
                appendLine("Target user ID: $targetUserId")
                appendLine("Requested: ${rows.size}")
                rows.forEach { r ->
                    appendLine("\n${r.permission}")
                    appendLine("  Actual: ${if (r.actualGranted) "GRANTED" else "DENIED"}")
                    appendLine("  DPC: ${r.dpcState}")
                    appendLine("  AppOp: ${r.appOpState ?: "N/A"}")
                    appendLine("  Route: ${r.route}")
                    appendLine("  Capability: ${r.capability}")
                    appendLine("  Group: ${r.group ?: "-"} protection=${r.protection}")
                }
            }
            postUiIfAlive { output.text = text }
        }.start()
    }

    private fun mutateSelected(state: ManagedPermissionState) {
        val pkg = targetPackage.text.toString().trim()
        val permission = query.text.toString().trim()
        if (pkg.isBlank() || permission.isBlank()) { output.text = "Target package and exact permission required"; return }
        val targetUserId = targetUserId()
        Thread {
            val entry = catalogEntry(permission)
            val shizukuAvailable = shizuku.identity() != null
            val decision = manager.resolveDecision(
                permission,
                targetUserId,
                state,
                entry?.protection ?: PermissionProtection.UNKNOWN,
                sensorGrantOptOut = false,
                shizukuAvailable = shizukuAvailable,
                systemPrivilegedAvailable = false,
                userActionAvailable = entry?.protection == PermissionProtection.SPECIAL_ACCESS
            )
            val text = when (decision.route) {
                PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC -> manager.setDpcPermissionState(pkg, permission, targetUserId, state).toString()
                PermissionControlRoute.SHIZUKU -> when (state) {
                    ManagedPermissionState.GRANTED -> "SHIZUKU_GRANT exit=${shizuku.grantRuntimePermission(pkg, permission, targetUserId)}"
                    ManagedPermissionState.DENIED -> "SHIZUKU_REVOKE exit=${shizuku.revokeRuntimePermission(pkg, permission, targetUserId)}"
                    ManagedPermissionState.DEFAULT -> "Default is a DPC policy state; Shizuku does not fake DPC DEFAULT"
                }
                PermissionControlRoute.USER_ACTION -> "USER_ACTION_REQUIRED"
                PermissionControlRoute.SYSTEM_PRIVILEGED -> "SYSTEM_PRIVILEGED route available only in matching build/runtime"
                PermissionControlRoute.UNAVAILABLE -> "${decision.capability}: ${decision.reason}"
            }
            val readback = manager.inspectPermission(pkg, permission, targetUserId, entry?.group, entry?.protection ?: PermissionProtection.UNKNOWN, shizukuAvailable = shizukuAvailable)
            postUiIfAlive {
                output.text = "$text\n\nReadback\nActual=${readback.actualGranted}\nDPC=${readback.dpcState}\nAppOp=${readback.appOpState}\nRoute=${readback.route}"
            }
        }.start()
    }

    private fun setGlobalPolicy(policy: GlobalRuntimePermissionPolicy) {
        Thread {
            val ok = manager.setGlobalPermissionPolicy(policy)
            val observed = runCatching { manager.getGlobalPermissionPolicy() }.getOrNull()
            postUiIfAlive { output.text = "Global policy requested=$policy observed=$observed verified=${ok && observed == policy}" }
        }.start()
    }

    private fun batchPreview(state: ManagedPermissionState) {
        val pkg = targetPackage.text.toString().trim()
        if (pkg.isBlank()) { output.text = "Target package required"; return }
        val userId = targetUserId()
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan().permissions.associateBy { it.name }
            val shizukuAvailable = shizuku.identity() != null
            val changes = requestedPermissions(pkg).map { permission ->
                val entry = snapshot[permission]
                val protection = entry?.protection ?: PermissionProtection.UNKNOWN
                val current = manager.inspectPermission(
                    packageName = pkg,
                    permission = permission,
                    targetUserId = userId,
                    group = entry?.group,
                    protection = protection,
                    shizukuAvailable = shizukuAvailable
                )
                val decision = manager.resolveDecision(
                    permission = permission,
                    targetUserId = userId,
                    desiredState = state,
                    protection = protection,
                    sensorGrantOptOut = false,
                    shizukuAvailable = shizukuAvailable,
                    systemPrivilegedAvailable = false,
                    userActionAvailable = entry?.protection == PermissionProtection.SPECIAL_ACCESS
                )
                PermissionBatchChange(pkg, permission, userId, current.dpcState, state, decision)
            }
            val plan = batchTransaction.plan(changes)
            pendingBatchChanges = changes
            pendingBatchPlan = plan
            val text = buildString {
                appendLine("Batch Preview")
                appendLine("Package=$pkg Target user ID=$userId requested=$state")
                appendLine("supported=${plan.supported.size} skipped=${plan.skipped.size}")
                plan.supported.forEach { item ->
                    appendLine("✓ ${item.permission}: ${item.previousState} -> ${item.requestedState} route=${item.route}")
                }
                plan.skipped.forEach { item ->
                    appendLine("× ${item.permission}: ${item.previousState} -> ${item.requestedState} SKIPPED ${item.detail}")
                }
            }
            postUiIfAlive { output.text = text }
        }.start()
    }

    private fun applyPendingBatch() {
        val plan = pendingBatchPlan ?: run { output.text = "Run Batch Preview first"; return }
        val pkg = targetPackage.text.toString().trim()
        Thread {
            val catalog = AndroidPermissionCatalog(this).scan().permissions.associateBy { it.name }
            val shizukuAvailable = shizuku.identity() != null
            val results = mutableListOf<String>()
            plan.supported.forEach { item ->
                val entry = catalog[item.permission]
                val applyDetail = when (item.route) {
                    PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC ->
                        manager.setDpcPermissionState(pkg, item.permission, item.userId, item.requestedState).detail
                    PermissionControlRoute.SHIZUKU -> when (item.requestedState) {
                        ManagedPermissionState.GRANTED -> "SHIZUKU_GRANT:${shizuku.grantRuntimePermission(pkg, item.permission, item.userId)}"
                        ManagedPermissionState.DENIED -> "SHIZUKU_REVOKE:${shizuku.revokeRuntimePermission(pkg, item.permission, item.userId)}"
                        ManagedPermissionState.DEFAULT -> "SKIPPED:DPC_DEFAULT_REQUIRES_DPC"
                    }
                    PermissionControlRoute.SYSTEM_PRIVILEGED -> "SKIPPED:SYSTEM_PRIVILEGED_ROUTE_NOT_ACTIVE"
                    PermissionControlRoute.USER_ACTION -> "SKIPPED:USER_ACTION_REQUIRED"
                    PermissionControlRoute.UNAVAILABLE -> "SKIPPED:UNAVAILABLE"
                }
                val readback = manager.inspectPermission(
                    packageName = pkg,
                    permission = item.permission,
                    targetUserId = item.userId,
                    group = entry?.group,
                    protection = entry?.protection ?: PermissionProtection.UNKNOWN,
                    shizukuAvailable = shizukuAvailable
                )
                val verified = when (item.route) {
                    PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC -> readback.dpcState == item.requestedState
                    PermissionControlRoute.SHIZUKU -> when (item.requestedState) {
                        ManagedPermissionState.GRANTED -> readback.actualGranted
                        ManagedPermissionState.DENIED -> !readback.actualGranted
                        ManagedPermissionState.DEFAULT -> false
                    }
                    else -> false
                }
                results += "${item.permission}: route=${item.route} $applyDetail verified=$verified Actual=${readback.actualGranted} DPC=${readback.dpcState} AppOp=${readback.appOpState}"
            }
            plan.skipped.forEach { results += "${it.permission}: SKIPPED ${it.detail}" }
            postUiIfAlive { output.text = "Apply supported\n${results.joinToString("\n")}" }
        }.start()
    }

    private fun restorePreviousDpcStates() {
        val restore = batchTransaction.restorePlan(pendingBatchChanges)
        if (restore.isEmpty()) { output.text = "No previous DPC-managed states to restore"; return }
        Thread {
            val results = restore.map { change ->
                val result = manager.setDpcPermissionState(
                    change.packageName,
                    change.permission,
                    change.userId,
                    change.requestedState
                )
                "${change.permission}: restore=${change.requestedState} observed=${result.observedDpcState} ${result.detail}"
            }
            postUiIfAlive { output.text = "Restore previous DPC states\n${results.joinToString("\n")}" }
        }.start()
    }

    private fun scanCatalog() {
        val q = query.text.toString().trim().lowercase()
        output.text = "Scanning..."
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan()
            val groups = snapshot.groups.filter { q.isBlank() || it.name.lowercase().contains(q) }
            val perms = snapshot.permissions.filter {
                q.isBlank() || it.name.lowercase().contains(q) || it.group?.lowercase()?.contains(q) == true || it.declaringPackage?.lowercase()?.contains(q) == true
            }
            val text = buildString {
                appendLine("PermissionManager: groups=${snapshot.groups.size}, permissions=${snapshot.permissions.size}")
                appendLine("Undocumented/vendor candidates=${snapshot.permissions.count(PermissionCatalogClassifier::isUndocumentedCandidate)}")
                appendLine("Known search hints: ${PermissionSeedQueries.groupHints.size + PermissionSeedQueries.permissionHints.size}")
                appendLine("\nGROUPS")
                groups.take(200).forEach { appendLine("${it.name}  pkg=${it.declaringPackage}  public=${it.publicSdkConstant}") }
                appendLine("\nPERMISSIONS")
                perms.take(1000).forEach {
                    appendLine("${it.name}\n  group=${it.group} pkg=${it.declaringPackage} protection=${it.protection} raw=0x${it.rawProtectionLevel.toString(16)} flags=0x${it.permissionFlags.toString(16)} public=${it.publicSdkConstant}")
                }
            }
            postUiIfAlive { output.text = text }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun autoRequestedPermissions() {
        val pkg = targetPackage.text.toString().trim()
        if (pkg.isBlank()) { output.text = "Target package required"; return }
        val userId = targetUserId()
        output.text = "Planning/applying verified routes..."
        Thread {
            val snapshot = AndroidPermissionCatalog(this).scan()
            val byName = snapshot.permissions.associateBy { it.name }
            val requested = requestedPermissions(pkg)
            val entries = requested.mapNotNull(byName::get)
            val coordinator = AndroidPermissionGrantCoordinator(this, admin, shizuku = shizuku)
            val results = coordinator.grantAllAuto(pkg, userId, entries, systemPrivilegedAvailable = false)
            val text = buildString {
                appendLine("AUTO permission result for $pkg")
                appendLine("requested=${requested.size} catalog-matched=${entries.size}")
                results.forEach { appendLine("${it.permission}: ${it.route} verified=${it.verified} ${it.detail}") }
            }
            postUiIfAlive { output.text = text }
        }.start()
    }

    private fun scanSystemConfigs() {
        shizuku.bind()
        output.postDelayed({
            postUiIfAlive {
                val permissions = shizuku.listPermissionConfigFiles() ?: "permission config unavailable"
                val sysconfig = shizuku.listSysconfigFiles() ?: "sysconfig unavailable"
                output.text = "PERMISSIONS XML\n$permissions\n\nSYSCONFIG XML\n$sysconfig"
            }
        }, 500)
    }

    private fun scanShizuku() {
        shizuku.bind()
        output.postDelayed({
            postUiIfAlive { output.text = shizuku.listPermissions() ?: "Shizuku not ready/authorized" }
        }, 500)
    }
}
