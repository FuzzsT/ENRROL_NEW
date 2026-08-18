package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AioDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        body.addView(TextView(this).apply { text = "DPC-AIO 0.6.0\nDevice Owner / policy / research toolkit" })
        add(body, "Activity Explorer") { ActivityExplorerActivity::class.java }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
        add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }
        add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }
        add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
        add(body, "NFC Lab") { NfcLabActivity::class.java }
        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        root.addView(Button(this).apply {
            text = label
            setOnClickListener { startActivity(Intent(this@AioDashboardActivity, target())) }
        })
    }
}
