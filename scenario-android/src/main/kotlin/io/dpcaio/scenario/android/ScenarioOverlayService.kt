package io.dpcaio.scenario.android

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.scenario.ReplayMode
import io.dpcaio.scenario.ReplayPlanner

class ScenarioOverlayService : Service() {
    private var root: LinearLayout? = null
    private val bridge = AndroidScenarioBridge()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bridge.install(application)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        root?.let { getSystemService(WindowManager::class.java).removeView(it) }
        root = null
        bridge.uninstall(application)
        super.onDestroy()
    }

    private fun showOverlay() {
        val status = TextView(this).apply { text = "Scenario: idle" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@ScenarioOverlayService).apply {
                text = "REC"
                setOnClickListener { bridge.clear(); status.text = "Scenario: recording" }
            })
            addView(Button(this@ScenarioOverlayService).apply {
                text = "STOP"
                setOnClickListener { status.text = "Scenario: ${bridge.snapshot().size} events" }
            })
            addView(Button(this@ScenarioOverlayService).apply {
                text = "REPLAY"
                setOnClickListener {
                    val plan = ReplayPlanner().plan(bridge.snapshot(), ReplayMode.DETERMINISTIC, 1.0)
                    status.text = "Replay plan: ${plan.steps.size} steps"
                }
            })
            addView(status)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END }
        getSystemService(WindowManager::class.java).addView(container, params)
        root = container
    }
}
