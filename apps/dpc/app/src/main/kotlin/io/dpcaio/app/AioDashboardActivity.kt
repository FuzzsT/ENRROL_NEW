package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AioDashboardActivity : Activity() {
    private val darkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO"

        val pageColor = if (darkMode) Color.rgb(15, 17, 21) else Color.rgb(246, 248, 251)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(pageColor)
        }

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        body.addView(buildHero(versionName))

        addSection(body, "Setup & enrollment")
        add(body, "Enrollment Engine") { EnrollmentManualActivity::class.java }
        add(body, "Full Offline Setup") { OfflineSetupActivity::class.java }
        add(body, "Enrollment Status") { EnrollmentStatusActivity::class.java }

        addSection(body, "Management")
        add(body, "Enterprise Policy Hub") { EnterprisePolicyHubActivity::class.java }
        add(body, "Enterprise Operations Center") { EnterpriseOperationsActivity::class.java }
        add(body, "Certificate & Credential Center") { CredentialCenterActivity::class.java }
        add(body, "Device Lifecycle Center") { DeviceLifecycleActivity::class.java }
        add(body, "Work Profile / COPE") { WorkProfileCopeActivity::class.java }
        add(body, "Knox Enterprise Center") { KnoxEnterpriseCenterActivity::class.java }

        addSection(body, "Tools")
        add(body, "Module Center (${DpcModuleRegistry.modules.size})") { ModuleCenterActivity::class.java }
        add(body, "Activity Explorer") { ActivityExplorerActivity::class.java }
        add(body, "Permission Manager") { PermissionManagerActivity::class.java }
        add(body, "Samsung Settings") { SamsungSettingsEditorActivity::class.java }
        add(body, "Google Account Manager") { GoogleAccountManagerActivity::class.java }
        add(body, "KnoxZT Framework") { KnoxZtManagerActivity::class.java }
        add(body, "Network / DNS / DoH") { NetworkControlActivity::class.java }
        add(body, "Diagnostics") { DpcDiagnosticsActivity::class.java }

        val preferences = DpcUiPreferences.read(this)
        if (preferences.developerMode) {
            addSection(body, "Advanced / Lab")
            add(body, "Scenario Recorder / Replay") { ScenarioLabActivity::class.java }
            add(body, "NFC Lab") { NfcLabActivity::class.java }
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(pageColor)
            addView(body)
        })
    }

    private fun buildHero(versionName: String): View {
        val accent = themeColor(android.R.attr.colorAccent, Color.rgb(64, 116, 255))
        val titleColor = if (darkMode) Color.WHITE else Color.rgb(24, 28, 36)
        val secondaryColor = if (darkMode) Color.rgb(190, 196, 208) else Color.rgb(82, 91, 108)
        val heroColor = if (darkMode) Color.rgb(31, 35, 43) else Color.WHITE

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(heroColor)
                setStroke(dp(1), withAlpha(accent, if (darkMode) 90 else 48))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(20) }

            addView(TextView(this@AioDashboardActivity).apply {
                text = "DPC-AIO"
                setTextColor(titleColor)
                textSize = 27f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@AioDashboardActivity).apply {
                text = "v$versionName  •  Device Owner / policy / research toolkit"
                setTextColor(secondaryColor)
                textSize = 13.5f
                setPadding(0, dp(5), 0, 0)
            })
            addView(TextView(this@AioDashboardActivity).apply {
                text = "Enterprise control center"
                setTextColor(accent)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.05f
                setPadding(0, dp(12), 0, 0)
            })
        }
    }

    private fun addSection(root: LinearLayout, label: String) {
        val accent = themeColor(android.R.attr.colorAccent, Color.rgb(64, 116, 255))
        root.addView(TextView(this).apply {
            text = label.uppercase()
            setTextColor(accent)
            textSize = 12f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(12), dp(4), dp(9))
        })
    }

    private fun add(root: LinearLayout, label: String, target: () -> Class<out Activity>) {
        val accent = themeColor(android.R.attr.colorAccent, Color.rgb(64, 116, 255))
        val cardColor = if (darkMode) Color.rgb(34, 38, 46) else Color.WHITE
        val borderColor = if (darkMode) Color.rgb(60, 65, 76) else Color.rgb(224, 228, 235)
        val textColor = if (darkMode) Color.rgb(239, 242, 248) else Color.rgb(31, 36, 45)

        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(textColor)
            textSize = 15f
            minHeight = dp(58)
            minimumHeight = dp(58)
            setPadding(dp(18), 0, dp(18), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(withAlpha(accent, if (darkMode) 72 else 42)),
                GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(cardColor)
                    setStroke(dp(1), borderColor)
                },
                null,
            )
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(9) }
            setOnClickListener {
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(70L).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                    startActivity(Intent(this@AioDashboardActivity, target()))
                }.start()
            }
        })
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) getColor(value.resourceId) else value.data
        } else {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
