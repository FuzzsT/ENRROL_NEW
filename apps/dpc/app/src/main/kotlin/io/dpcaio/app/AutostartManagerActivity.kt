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
        val manifestEnabled: Boolean,
    )

    private lateinit var targetUser: EditText
    private lateinit var output: TextView
    private lateinit var listRoot: LinearLayout
    private val shizuku by lazy { ShizukuUserServiceClient(this) }
    private val localGateway by lazy { AndroidComponentStateGateway(this) }

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
        output.text = "Scanning boot receivers..."
        Thread {
            val actions = listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)
            val rows = actions.flatMap { action ->
                queryBroadcastReceivers(Intent(action)).mapNotNull { resolve ->
                    val info = resolve.activityInfo ?: return@mapNotNull null
                    BootReceiver(
                        component = ComponentName(info.packageName, info.name),
                        label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.name),
                        action = action,
                        manifestEnabled = info.enabled,
                    )
                }
            }.distinctBy { it.component.flattenToString() }
                .sortedWith(
                    compareBy<BootReceiver>(String.CASE_INSENSITIVE_ORDER) { it.label }
                        .thenBy { it.component.flattenToString() },
                )

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderRows(rows)
                output.text = "Found ${rows.size} boot receivers • target user ${targetUserId()}"
            }
        }.start()
    }

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

    private fun renderRows(rows: List<BootReceiver>) {
        listRoot.removeAllViews()
        rows.forEach { row ->
            val readback = runCatching { localGateway.readback(row.component, row.manifestEnabled) }.getOrNull()
            val protected = row.component in protectedComponents
            listRoot.addView(ModernUi.card(this, emphasized = readback?.effectiveEnabled == true).apply {
                addView(ModernUi.title(this@AutostartManagerActivity, row.label))
                addView(ModernUi.body(
                    this@AutostartManagerActivity,
                    "${row.component.flattenToShortString()}\n${row.action}\n" +
                        "Manifest=${row.manifestEnabled} Override=${readback?.overrideState ?: "unknown"} Effective=${readback?.effectiveEnabled ?: "unknown"}" +
                        if (protected) "\nPROTECTED_DPC_COMPONENT" else "",
                ))
                addView(ModernUi.action(this@AutostartManagerActivity, "Enable") {
                    mutate(row, ComponentOverrideState.ENABLED)
                }.apply { isEnabled = !protected })
                addView(ModernUi.action(this@AutostartManagerActivity, "Disable", destructive = true) {
                    mutate(row, ComponentOverrideState.DISABLED)
                }.apply { isEnabled = !protected })
            })
        }
    }

    private fun mutate(row: BootReceiver, state: ComponentOverrideState) {
        if (row.component in protectedComponents) {
            output.text = "Protected DPC/recovery component cannot be changed here."
            return
        }
        val userId = targetUserId()
        output.text = "Applying ${state.name} to ${row.component.flattenToShortString()}..."
        Thread {
            val samePackage = row.component.packageName == packageName
            val detail = if (samePackage && userId == AndroidUserId.fromUid(Process.myUid())) {
                val result = localGateway.setState(row.component, row.manifestEnabled, state, dontKillApp = true)
                "LOCAL accepted=${result.accepted} observed=${result.observedState} ${result.detail}"
            } else {
                val ready = shizuku.awaitConnected(1500L)
                if (!ready) {
                    "SHIZUKU_NOT_READY"
                } else {
                    val result = ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(
                        row.component.packageName,
                        row.component.className,
                        userId,
                        state,
                    )
                    "SHIZUKU submitted=${result.submitted} exit=${result.exitCode} ${result.detail}"
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                output.text = detail
                scan()
            }
        }.start()
    }
}
