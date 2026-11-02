package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox

class DpcSettingsActivity : Activity() {
    private val installAuthorization by lazy { InstallAuthorizationCoordinator(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO Settings"
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val ui = DpcUiPreferences.read(this)
        val native = NativeSupportManager.probe(this)
        val body = ModernUi.page(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "Settings",
                subtitle = "Normal-mode defaults, package-install protection and native integration.",
                eyebrow = "DPC-AIO options",
            )
        )

        body.addView(ModernUi.section(this, "Package installation"))
        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(CheckBox(this@DpcSettingsActivity).apply {
                    text = "Require PIN before installing packages"
                    isChecked = installAuthorization.isEnabled()
                    setOnCheckedChangeListener { _, checked ->
                        installAuthorization.setEnabled(checked)
                    }
                })
                addView(
                    ModernUi.detailRow(
                        this@DpcSettingsActivity,
                        "Current behavior",
                        if (installAuthorization.isEnabled()) "PIN required for each queued install" else "Install queue does not request PIN",
                    )
                )
                addView(ModernUi.action(this@DpcSettingsActivity, "Configure / change PIN") {
                    startActivity(Intent(this@DpcSettingsActivity, PinGuardActivity::class.java))
                })
            }
        )

        body.addView(ModernUi.section(this, "Home & interface"))
        body.addView(
            ModernUi.card(this).apply {
                addView(CheckBox(this@DpcSettingsActivity).apply {
                    text = "Show advanced tools on the main screen"
                    isChecked = ui.showAdvancedHome
                    setOnCheckedChangeListener { _, checked -> DpcUiPreferences.setShowAdvancedHome(this@DpcSettingsActivity, checked) }
                })
                addView(CheckBox(this@DpcSettingsActivity).apply {
                    text = "Developer mode"
                    isChecked = ui.developerMode
                    setOnCheckedChangeListener { _, checked -> DpcUiPreferences.setDeveloperMode(this@DpcSettingsActivity, checked) }
                })
                addView(CheckBox(this@DpcSettingsActivity).apply {
                    text = "Show experimental modules"
                    isChecked = ui.showExperimental
                    setOnCheckedChangeListener { _, checked -> DpcUiPreferences.setShowExperimental(this@DpcSettingsActivity, checked) }
                })
            }
        )

        body.addView(ModernUi.section(this, "Native support"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.detailRow(this@DpcSettingsActivity, "Detected", native.summary()))
                addView(ModernUi.detailRow(this@DpcSettingsActivity, "Policy owner", if (native.hasPolicyOwner) "AVAILABLE" else "Not active"))
                addView(ModernUi.detailRow(this@DpcSettingsActivity, "Binder bridge", if (native.hasBinderBridge) "Dhizuku / Shizuku detected" else "Not detected"))
                addView(ModernUi.detailRow(this@DpcSettingsActivity, "app_process", native.appProcessPath ?: "Not available"))
                addView(CheckBox(this@DpcSettingsActivity).apply {
                    text = "Enable guarded native JAR calling"
                    isChecked = ui.nativeJarCallingEnabled
                    isEnabled = native.jarCallingAvailable
                    setOnCheckedChangeListener { _, checked ->
                        DpcUiPreferences.setNativeJarCallingEnabled(this@DpcSettingsActivity, checked)
                    }
                })
                addView(ModernUi.action(this@DpcSettingsActivity, "Open Native JAR Runner") {
                    startActivity(Intent(this@DpcSettingsActivity, NativeJarRunnerActivity::class.java))
                }.apply {
                    isEnabled = native.jarCallingAvailable && ui.nativeJarCallingEnabled
                })
            }
        )

        body.addView(ModernUi.section(this, "Updates"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.action(this@DpcSettingsActivity, "Open Update Center", primary = true) {
                    startActivity(Intent(this@DpcSettingsActivity, SelfUpdateCenterActivity::class.java))
                })
                addView(
                    ModernUi.detailRow(
                        this@DpcSettingsActivity,
                        "Verification",
                        "HTTPS • SHA-256 • package • version • signer • PackageInstaller",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
    }
}
