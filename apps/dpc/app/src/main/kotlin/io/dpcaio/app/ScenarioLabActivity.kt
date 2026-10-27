package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import io.dpcaio.scenario.android.ScenarioOverlayService

class ScenarioLabActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Scenario Recorder / Replay"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Scenario Recorder / Replay",
                subtitle = "Developer overlay controls for recording and replay workflows",
                eyebrow = "DEVELOPER LAB",
            )
        )

        body.addView(ModernUi.section(this, "Overlay access"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.body(
                        this@ScenarioLabActivity,
                        "The recorder requires Android's explicit draw-over-other-apps permission before the overlay service can start.",
                    )
                )
                addView(ModernUi.action(this@ScenarioLabActivity, "Grant overlay access") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                })
            }
        )

        body.addView(ModernUi.section(this, "Recorder"))
        body.addView(
            ModernUi.card(this).apply {
                addView(
                    ModernUi.actionRow(
                        this@ScenarioLabActivity,
                        ModernUi.action(this@ScenarioLabActivity, "Start REC overlay", primary = true) { startOverlay() },
                        ModernUi.action(this@ScenarioLabActivity, "Stop overlay", destructive = true) {
                            stopService(Intent(this@ScenarioLabActivity, ScenarioOverlayService::class.java))
                            status.text = "overlay stopped"
                        },
                    )
                )
            }
        )

        body.addView(ModernUi.section(this, "Status"))
        status = TextView(this).apply {
            text = if (Settings.canDrawOverlays(this@ScenarioLabActivity)) "overlay permission granted" else "overlay permission required"
            setTextIsSelectable(true)
        }
        body.addView(ModernUi.outputCard(this, status))
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "overlay permission required"
            return
        }
        startService(Intent(this, ScenarioOverlayService::class.java))
        status.text = "overlay started"
    }
}
