package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.scenario.android.ScenarioOverlayService

class ScenarioLabActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Scenario Recorder / Replay"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        status = TextView(this)
        root.addView(Button(this).apply { text = "Grant overlay access"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Start REC overlay"; setOnClickListener { startOverlay() } })
        root.addView(Button(this).apply { text = "Stop overlay"; setOnClickListener { stopService(Intent(this@ScenarioLabActivity, ScenarioOverlayService::class.java)); status.text = "overlay stopped" } })
        root.addView(status)
        setContentView(root)
    }
    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) { status.text = "overlay permission required"; return }
        startService(Intent(this, ScenarioOverlayService::class.java)); status.text = "overlay started"
    }
}
