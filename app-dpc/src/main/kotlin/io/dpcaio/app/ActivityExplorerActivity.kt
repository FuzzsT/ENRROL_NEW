package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.activity.ActivityAccessInput
import io.dpcaio.activity.ActivityAccessPlanner
import io.dpcaio.activity.ActivityExecutorRouter
import io.dpcaio.activity.ActivityLaunchCoordinator
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.DiscoveredActivity
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.activity.android.AndroidActivityRouteExecutor
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import io.dpcaio.knoxzt.android.KnoxZtRecoveryManager
import io.dpcaio.shizuku.AndroidShizukuRuntime
import io.dpcaio.shizuku.ShizukuActivityRouteExecutor
import io.dpcaio.shizuku.ShizukuUserServiceClient

class ActivityExplorerActivity : Activity() {
    private lateinit var packageNameInput: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Activity Explorer"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        packageNameInput = EditText(this).apply { hint = "package name"; setText(intent.getStringExtra("package") ?: KNOXZT_PACKAGE) }
        status = TextView(this).apply { setTextIsSelectable(true) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(packageNameInput)
        root.addView(Button(this).apply { text = "Scan activities"; setOnClickListener { scan() } })
        root.addView(status)
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(root)
        shizuku.bind()
    }

    private fun scan() {
        val pkg = packageNameInput.text.toString().trim()
        if (pkg.isBlank()) return
        status.text = "Scanning $pkg..."
        Thread {
            if (pkg == KNOXZT_PACKAGE) {
                KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = shizuku).ensureReady()
            }
            val activities = runCatching { AndroidActivityInventory(this).list(pkg, Process.myUserHandle()) }.getOrElse { emptyList() }
            runOnUiThread {
                list.removeAllViews()
                status.text = "activities=${activities.size}"
                activities.forEach { activity ->
                    list.addView(Button(this).apply {
                        text = "${activity.className}\nexported=${activity.exported} enabled=${activity.enabled} permission=${activity.requiredPermission ?: "-"}"
                        setOnClickListener { launch(activity) }
                    })
                }
            }
        }.start()
    }

    private fun launch(activity: DiscoveredActivity) {
        val shizukuState = AndroidShizukuRuntime().probe()
        val input = ActivityAccessInput(
            packageName = activity.packageName,
            className = activity.className,
            enabled = activity.enabled,
            exported = activity.exported,
            launcherVisible = activity.launcherVisible,
            sameUid = activity.sameUid,
            userAccessible = activity.userAccessible,
            shizukuAccessible = shizukuState.binderAlive && shizukuState.permissionGranted
        )
        val executor = ActivityExecutorRouter(
            AndroidActivityRouteExecutor(this, Process.myUserHandle()),
            mapOf(ActivityRoute.SHIZUKU to ShizukuActivityRouteExecutor(shizuku, android.os.UserHandle.getUserId(Process.myUid())))
        )
        val result = ActivityLaunchCoordinator(ActivityAccessPlanner(), executor).launch(input)
        status.text = "selected=${result.selectedRoute}\nblockers=${result.blockers}\nattempts=${result.attempts.joinToString { "${it.route}:${it.success}" }}"
    }
}
