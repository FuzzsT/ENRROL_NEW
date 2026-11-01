package io.dpcaio.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.activity.ComponentMutationVerification
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.android.AndroidComponentStateGateway
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuComponentStateExecutor
import io.dpcaio.shizuku.ShizukuUserServiceClient

class AutostartManagerActivity : Activity() {
    private data class BootReceiver(
        val component: ComponentName,
        val label: String,
        val action: String,
        val manifestEnabled: Boolean?,
        val inventorySource: String,
    )

    private data class ReceiverInventory(
        val rows: List<BootReceiver>,
        val source: String,
        val error: String? = null,
    )

    private lateinit var targetUser: EditText
    private lateinit var output: TextView
    private lateinit var listRoot: LinearLayout
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val localGateway by lazy { AndroidComponentStateGateway(this) }
    private val submittedTargetStates = mutableMapOf<String, ComponentOverrideState>()
    private var scanGeneration = 0L
    private var mutationGeneration = 0L

    private val protectedComponents by lazy {
        setOf(
            ComponentName(this, AioDeviceAdminReceiver::class.java),
            ComponentName(this, EnrollmentRecoveryReceiver::class.java),
            ComponentName(this, OfflineRecoveryReceiver::class.java),
            ComponentName(this, KnoxBootReceiver::class.java),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Autostart Manager"

        val body = LinearLayout(this)
        body.addView(ModernUi.hero(
            this,
            title = "Autostart Manager",
            subtitle = "Inspect boot receivers and manage their component state.",
            eyebrow = "BOOT_COMPLETED control",
        ))

        body.addView(ModernUi.section(this, "Target"))
        body.addView(ModernUi.card(this, emphasized = true).apply {
            targetUser = ModernUi.field(this@AutostartManagerActivity, "Target user ID", InputType.TYPE_CLASS_NUMBER).apply {
                setText(AndroidUserId.fromUid(Process.myUid()).toString())
            }
            addView(targetUser)
            addView(ModernUi.spacer(this@AutostartManagerActivity, 8))
            addView(ModernUi.action(this@AutostartManagerActivity, "Choose user") {
                val current = AndroidUserId.fromUid(Process.myUid())
                RuntimeTargetPicker.showUserPicker(
                    this@AutostartManagerActivity,
                    shizuku,
                    current,
                    targetUserId(),
                ) { selected ->
                    targetUser.setText(selected.toString())
                    scan()
                }
            })
            addView(ModernUi.spacer(this@AutostartManagerActivity, 8))
            addView(ModernUi.action(this@AutostartManagerActivity, "Scan autostart") { scan() })
        })

        body.addView(ModernUi.section(this, "Boot receivers"))
        listRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listRoot)

        body.addView(ModernUi.section(this, "Status"))
        output = TextView(this).apply { text = "Ready." }
        body.addView(ModernUi.outputCard(this, output))

        setContentView(ModernUi.page(this, body))
        shizuku.bind()
        scan()
    }

    private fun targetUserId(): Int = targetUser.text.toString().trim().toIntOrNull()?.takeIf { it >= 0 }
        ?: AndroidUserId.fromUid(Process.myUid())

    private fun scan() {
        val generation = ++scanGeneration
        val selectedUser = targetUserId()
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        output.text = "Scanning boot receivers for target user $selectedUser..."
        Thread {
            val inventory = if (selectedUser == currentUser) {
                queryCurrentUserBootReceivers(selectedUser)
            } else {
                queryCrossUserBootReceivers(selectedUser)
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != scanGeneration) return@runOnUiThread
                renderRows(inventory.rows, selectedUser)
                output.text = if (inventory.error != null) {
                    "Cross-user inventory unavailable for user $selectedUser: ${inventory.error}"
                } else {
                    "Found ${inventory.rows.size} boot receivers • target user $selectedUser • source=${inventory.source}"
                }
            }
        }.start()
    }

    private fun queryCurrentUserBootReceivers(userId: Int): ReceiverInventory {
        val local = queryLocalBootReceivers()
        val privileged = queryCrossUserBootReceivers(userId)
        if (privileged.error != null) {
            return ReceiverInventory(local, LOCAL_PACKAGE_MANAGER)
        }
        val localByComponent = local.associateBy { it.component.flattenToString() }
        val shizukuOnly = privileged.rows.filterNot { it.component.flattenToString() in localByComponent }
            .map { it.copy(inventorySource = CURRENT_USER_SHIZUKU_AUGMENTED) }
        return ReceiverInventory(
            sortRows(local + shizukuOnly),
            if (shizukuOnly.isEmpty()) LOCAL_PACKAGE_MANAGER else CURRENT_USER_SHIZUKU_AUGMENTED,
        )
    }

    private fun queryLocalBootReceivers(): List<BootReceiver> = sortRows(
        BOOT_ACTIONS.flatMap { action ->
            queryBroadcastReceivers(Intent(action)).mapNotNull { resolve ->
                val info = resolve.activityInfo ?: return@mapNotNull null
                BootReceiver(
                    component = ComponentName(info.packageName, info.name),
                    label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.name),
                    action = action,
                    manifestEnabled = info.enabled,
                    inventorySource = LOCAL_PACKAGE_MANAGER,
                )
            }
        }
    )

    private fun queryCrossUserBootReceivers(userId: Int): ReceiverInventory {
        if (!shizuku.awaitConnected(1800L)) {
            return ReceiverInventory(emptyList(), CROSS_USER_SHIZUKU, "Shizuku user service not connected")
        }

        val rows = mutableListOf<BootReceiver>()
        for (action in BOOT_ACTIONS) {
            val raw = shizuku.queryBroadcastReceivers(action, userId)
                ?: return ReceiverInventory(emptyList(), CROSS_USER_SHIZUKU, "Shizuku receiver query unavailable")
            val normalized = raw.trim()
            if (normalized in QUERY_FAILURE_MARKERS) {
                return ReceiverInventory(emptyList(), CROSS_USER_SHIZUKU, "receiver query failed: $normalized")
            }
            rows += parseCrossUserReceiverComponents(raw, action)
        }
        return ReceiverInventory(sortRows(rows), CROSS_USER_SHIZUKU)
    }

    private fun parseCrossUserReceiverComponents(raw: String, action: String): List<BootReceiver> =
        raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull(ComponentName::unflattenFromString)
            .map { component ->
                BootReceiver(
                    component = component,
                    label = component.className.substringAfterLast('.'),
                    action = action,
                    manifestEnabled = null,
                    inventorySource = CROSS_USER_SHIZUKU,
                )
            }
            .toList()

    private fun sortRows(rows: List<BootReceiver>): List<BootReceiver> =
        rows.distinctBy { it.component.flattenToString() }
            .sortedWith(Comparator { left, right ->
                val byLabel = String.CASE_INSENSITIVE_ORDER.compare(left.label, right.label)
                if (byLabel != 0) byLabel else left.component.flattenToString().compareTo(right.component.flattenToString())
            })

    @Suppress("DEPRECATION")
    private fun queryBroadcastReceivers(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }

    private fun renderRows(rows: List<BootReceiver>, selectedUser: Int = targetUserId()) {
        listRoot.removeAllViews()
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        rows.forEach { row ->
            val readback = if (selectedUser == currentUser && row.manifestEnabled != null) {
                runCatching { localGateway.readback(row.component, row.manifestEnabled) }.getOrNull()
            } else {
                null
            }
            val submitted = submittedTargetStates[stateKey(selectedUser, row.component)]
            val protected = row.component in protectedComponents
            listRoot.addView(ModernUi.card(this, emphasized = readback?.effectiveEnabled == true || submitted == ComponentOverrideState.ENABLED).apply {
                addView(ModernUi.title(this@AutostartManagerActivity, row.label))
                val stateText = if (selectedUser == currentUser) {
                    "CURRENT_USER_READBACK user=$selectedUser • source=${row.inventorySource} • Manifest=${row.manifestEnabled ?: "unknown"} Override=${readback?.overrideState ?: "unknown"} Effective=${readback?.effectiveEnabled ?: "unknown"}"
                } else {
                    "TARGET_USER_INVENTORY user=$selectedUser • source=${row.inventorySource} • lastVerified=${submitted?.name ?: "none"} • Manifest=unknown • liveReadback=mutation-only"
                }
                addView(ModernUi.body(
                    this@AutostartManagerActivity,
                    "${row.component.flattenToShortString()}\n${row.action}\n$stateText" +
                        if (protected) "\nPROTECTED_DPC_COMPONENT" else "",
                ))
                addView(ModernUi.action(this@AutostartManagerActivity, "Enable") {
                    mutate(row, ComponentOverrideState.ENABLED)
                }.apply { isEnabled = !protected })
                addView(ModernUi.action(this@AutostartManagerActivity, "Disable", destructive = true) {
                    mutate(row, ComponentOverrideState.DISABLED)
                }.apply { isEnabled = !protected })
                addView(ModernUi.action(this@AutostartManagerActivity, "Restore default") {
                    mutate(row, ComponentOverrideState.DEFAULT)
                }.apply { isEnabled = !protected })
            })
        }
    }

    private fun stateKey(userId: Int, component: ComponentName): String =
        "$userId:${component.flattenToString()}"

    private fun mutate(row: BootReceiver, state: ComponentOverrideState) {
        if (row.component in protectedComponents) {
            output.text = "Protected DPC/recovery component cannot be changed here."
            return
        }
        val userId = targetUserId()
        val generation = ++mutationGeneration
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        output.text = "Applying ${state.name} to ${row.component.flattenToShortString()} for target user $userId..."
        Thread {
            val samePackage = row.component.packageName == packageName
            val detail = if (samePackage && userId == currentUser) {
                val manifestEnabled = row.manifestEnabled
                if (manifestEnabled == null) {
                    "LOCAL_MANIFEST_STATE_UNAVAILABLE"
                } else {
                    val result = localGateway.setState(row.component, manifestEnabled, state, dontKillApp = true)
                    "CURRENT_USER_READBACK accepted=${result.accepted} requested=$state observed=${result.observedState} effective=${result.effectiveEnabled} ${result.detail}"
                }
            } else {
                val ready = shizuku.awaitConnected(1800L)
                if (!ready) {
                    "SHIZUKU_NOT_READY"
                } else {
                    val result = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(
                        row.component.packageName,
                        row.component.className,
                        userId,
                        state,
                    )
                    val verification = ComponentMutationVerification.statusLine(
                        route = "SHIZUKU",
                        userId = userId,
                        requested = state,
                        observed = result.observedState,
                        verified = result.verified,
                        detail = result.detail,
                    )
                    if (result.verified) {
                        "TARGET_USER_VERIFIED $verification"
                    } else {
                        "TARGET_USER_FAILED $verification"
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (generation != mutationGeneration) return@runOnUiThread
                if (userId != targetUserId()) return@runOnUiThread
                if (detail.startsWith("TARGET_USER_VERIFIED")) {
                    submittedTargetStates[stateKey(userId, row.component)] = state
                }
                output.text = detail
                scan()
            }
        }.start()
    }

    companion object {
        private const val LOCAL_PACKAGE_MANAGER = "LOCAL_PACKAGE_MANAGER"
        private const val CURRENT_USER_SHIZUKU_AUGMENTED = "CURRENT_USER_SHIZUKU_AUGMENTED"
        private const val CROSS_USER_SHIZUKU = "CROSS_USER_SHIZUKU"
        private val BOOT_ACTIONS = listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)
        private val QUERY_FAILURE_MARKERS = setOf("INVALID_ARGUMENT", "EXECUTION_ERROR", "TIMEOUT")
    }
}
