package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.activity.DeveloperActivityClassifier
import io.dpcaio.activity.DiscoveredActivity
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuUserServiceClient

class DeveloperActivitiesActivity : Activity() {
    private lateinit var packageInput: EditText
    private lateinit var userInput: EditText
    private lateinit var listRoot: LinearLayout
    private lateinit var status: TextView
    private val shizuku by lazy { ShizukuUserServiceClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Developer Activities"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "Hidden Developer Activities",
                subtitle = "Find debug, developer, engineering, diagnostic and test components from package metadata.",
                eyebrow = "DISCOVERY ONLY • GUARDED LAUNCH",
            )
        )

        body.addView(ModernUi.section(this, "Target package"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                packageInput = ModernUi.field(this@DeveloperActivitiesActivity, "Package name")
                userInput = ModernUi.field(
                    this@DeveloperActivitiesActivity,
                    "Target user ID",
                    InputType.TYPE_CLASS_NUMBER,
                ).apply {
                    setText(AndroidUserId.fromUid(Process.myUid()).toString())
                }
                addView(packageInput)
                addView(userInput)
                addView(ModernUi.spacer(this@DeveloperActivitiesActivity, 8))
                addView(
                    ModernUi.actionRow(
                        this@DeveloperActivitiesActivity,
                        ModernUi.action(this@DeveloperActivitiesActivity, "Choose package") {
                            RuntimeTargetPicker.showPackagePicker(
                                this@DeveloperActivitiesActivity,
                                shizuku,
                                targetUserId(),
                                packageInput.text.toString().trim(),
                            ) { selected ->
                                packageInput.setText(selected)
                                scan()
                            }
                        },
                        ModernUi.action(this@DeveloperActivitiesActivity, "Choose user") {
                            val current = AndroidUserId.fromUid(Process.myUid())
                            RuntimeTargetPicker.showUserPicker(
                                this@DeveloperActivitiesActivity,
                                shizuku,
                                current,
                                targetUserId(),
                            ) { selected ->
                                userInput.setText(selected.toString())
                                if (packageInput.text.isNotBlank()) scan()
                            }
                        },
                    )
                )
                addView(ModernUi.spacer(this@DeveloperActivitiesActivity, 8))
                addView(
                    ModernUi.action(this@DeveloperActivitiesActivity, "Scan developer activities", primary = true) {
                        scan()
                    }
                )
            }
        )

        body.addView(ModernUi.section(this, "Matches"))
        listRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listRoot)

        body.addView(ModernUi.section(this, "Status"))
        status = TextView(this).apply { text = "Choose a package or enter one manually, then scan." }
        body.addView(ModernUi.outputCard(this, status))

        setContentView(ModernUi.scrollPage(this, body))
        shizuku.bind()
    }

    private fun targetUserId(): Int = userInput.text.toString().trim().toIntOrNull()?.takeIf { it >= 0 }
        ?: AndroidUserId.fromUid(Process.myUid())

    private fun scan() {
        val packageName = packageInput.text.toString().trim()
        if (packageName.isBlank()) {
            status.text = "Package name is required."
            return
        }
        val userId = targetUserId()
        status.text = "Scanning $packageName for developer-like activities..."
        Thread {
            val rows = runCatching {
                AndroidActivityInventory(this).list(
                    packageName,
                    UserHandle.getUserHandleForUid(AndroidUserId.representativeUid(userId)),
                ).filter(DeveloperActivityClassifier::isDeveloperLike)
            }.getOrElse { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderRows(rows, userId)
                status.text = "Found ${rows.size} developer-like activities in $packageName for user $userId."
            }
        }.start()
    }

    private fun renderRows(rows: List<DiscoveredActivity>, userId: Int) {
        listRoot.removeAllViews()
        if (rows.isEmpty()) {
            listRoot.addView(
                ModernUi.compactCard(this).apply {
                    addView(ModernUi.body(this@DeveloperActivitiesActivity, "No developer/debug/engineering activities matched the metadata classifier."))
                }
            )
            return
        }
        rows.sortedBy { it.className.lowercase() }.forEach { activity ->
            listRoot.addView(
                ModernUi.compactCard(this, if (activity.effectiveEnabled) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING).apply {
                    addView(ModernUi.title(this@DeveloperActivitiesActivity, activity.className.substringAfterLast('.')))
                    addView(
                        ModernUi.body(
                            this@DeveloperActivitiesActivity,
                            "${activity.className}\nexported=${activity.exported} enabled=${activity.effectiveEnabled} permission=${activity.requiredPermission ?: "none"}",
                        )
                    )
                    addView(ModernUi.spacer(this@DeveloperActivitiesActivity, 8))
                    addView(
                        ModernUi.action(this@DeveloperActivitiesActivity, "Open package in Activity Explorer") {
                            startActivity(
                                Intent(this@DeveloperActivitiesActivity, ActivityExplorerActivity::class.java)
                                    .putExtra("package", activity.packageName)
                                    .putExtra("userId", userId)
                            )
                        }
                    )
                }
            )
        }
    }
}
