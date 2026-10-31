package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.Executors

class AppLockActivity : Activity() {
    private lateinit var packageNameField: EditText
    private lateinit var pin: EditText
    private lateinit var status: TextView
    private var operationResult: AppLockResult? = null
    private val actionButtons = mutableListOf<Button>()
    private val executor = Executors.newSingleThreadExecutor()
    private val coordinator by lazy { AppLockCoordinator(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "App Lock"

        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "App Lock",
                subtitle = "Device Owner package suspension with PIN Guard authorization for unlocks.",
                eyebrow = "APP SECURITY",
            )
        )

        body.addView(ModernUi.section(this, "Target"))
        body.addView(
            ModernUi.card(this).apply {
                packageNameField = ModernUi.field(this@AppLockActivity, "Package name").apply {
                    setText(intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty())
                }
                addView(packageNameField)
            }
        )
        status = ModernUi.outputCard(this, "App Lock ready")
        body.addView(status)

        body.addView(ModernUi.section(this, "Authorization"))
        body.addView(
            ModernUi.card(this).apply {
                pin = ModernUi.field(
                    this@AppLockActivity,
                    "PIN Guard PIN",
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                ).apply { filters = arrayOf(InputFilter.LengthFilter(12)) }
                addView(pin)
                addAction("Lock app", destructive = true) { runLock() }
                addAction("Unlock permanently") { runUnlock(null) }
                addAction("Unlock 1 min") { runUnlock(ONE_MINUTE_MS) }
                addAction("Unlock 5 min") { runUnlock(FIVE_MINUTES_MS) }
                addAction("Unlock 15 min") { runUnlock(FIFTEEN_MINUTES_MS) }
            }
        )

        body.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@AppLockActivity, "Platform boundary"))
                addView(
                    ModernUi.body(
                        this@AppLockActivity,
                        "App Lock uses Android DevicePolicyManager suspension. Unlock requires the local PIN Guard. It does not use overlays or Accessibility interception.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, body))
        reconcileAndRefresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) reconcileAndRefresh()
    }

    private fun android.widget.LinearLayout.addAction(
        label: String,
        destructive: Boolean = false,
        action: () -> Unit,
    ) {
        addView(ModernUi.spacer(this@AppLockActivity, 8))
        val button = ModernUi.action(this@AppLockActivity, label, destructive = destructive) { action() }
        actionButtons += button
        addView(button)
    }

    private fun targetPackage(): String = packageNameField.text?.toString().orEmpty().trim()

    private fun runLock() {
        val target = targetPackage()
        if (target.isBlank()) return showStatus("Enter package name")
        runBusy("Locking $target…", target) { coordinator.lock(target) }
    }

    private fun runUnlock(durationMillis: Long?) {
        val target = targetPackage()
        if (target.isBlank()) return showStatus("Enter package name")
        val entered = pin.text?.toString().orEmpty().toCharArray()
        if (entered.isEmpty()) return showStatus("Enter PIN Guard PIN")
        runBusy("Authorizing unlock…", target) { coordinator.unlock(target, entered, durationMillis) }
    }

    private fun runBusy(message: String, target: String, operation: () -> AppLockResult) {
        setBusy(true)
        showStatus(message)
        executor.execute {
            val result = runCatching(operation).getOrElse { AppLockResult.POLICY_REJECTED }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pin.text?.clear()
                setBusy(false)
                operationResult = result
                refreshState(target, operationResult)
            }
        }
    }

    private fun reconcileAndRefresh() {
        val target = targetPackage()
        executor.execute {
            coordinator.reconcileExpiredUnlocks()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (target.isNotBlank()) refreshState(target, null)
            }
        }
    }

    private fun refreshState(target: String = targetPackage(), operationResult: AppLockResult? = null) {
        if (target.isBlank()) {
            showStatus("Enter package name")
            return
        }
        val record = coordinator.state(target)
        val detail = when {
            record == null -> "not managed by App Lock"
            record.unlockedUntilMillis > System.currentTimeMillis() -> "temporarily unlocked until ${record.unlockedUntilMillis}"
            record.locked -> "locked"
            else -> "unlocked"
        }
        val operation = operationResult?.let { "Operation: ${it.name} • " }.orEmpty()
        showStatus("${operation}Target: $target • $detail")
    }

    private fun setBusy(busy: Boolean) {
        actionButtons.forEach {
            it.isEnabled = !busy
            it.alpha = if (busy) 0.55f else 1f
        }
        if (::packageNameField.isInitialized) packageNameField.isEnabled = !busy
    }

    private fun showStatus(message: String) {
        status.text = message
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "app_lock_target_package"
        private const val ONE_MINUTE_MS = 60_000L
        private const val FIVE_MINUTES_MS = 5L * ONE_MINUTE_MS
        private const val FIFTEEN_MINUTES_MS = 15L * ONE_MINUTE_MS
    }
}
