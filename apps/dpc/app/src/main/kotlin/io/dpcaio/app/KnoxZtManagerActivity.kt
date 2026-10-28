package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.widget.EditText
import android.widget.TextView
import io.dpcaio.knoxzt.android.KnoxZtActivitySupport
import io.dpcaio.knoxzt.android.KnoxZtInstallSource
import io.dpcaio.knoxzt.android.KnoxZtInstallSourceStore
import io.dpcaio.knoxzt.android.KnoxZtRecoveryManager
import io.dpcaio.shizuku.ShizukuUserServiceClient

class KnoxZtManagerActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var url: EditText
    private lateinit var apkSha: EditText
    private lateinit var signerSha: EditText

    companion object {
        private val operationLock = Any()
        private var operationRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "KnoxZT Framework"
        val body = ModernUi.page(this)
        body.addView(
            ModernUi.hero(
                this,
                title = "KnoxZT Framework",
                subtitle = "Trusted source, recovery and activity inventory for the official Knox Zero Trust package",
                eyebrow = "SAMSUNG / KNOXZT",
            )
        )

        body.addView(ModernUi.section(this, "Trusted source"))
        body.addView(
            ModernUi.card(this).apply {
                url = ModernUi.field(this@KnoxZtManagerActivity, "Trusted HTTPS APK URL")
                apkSha = ModernUi.field(this@KnoxZtManagerActivity, "APK SHA-256")
                signerSha = ModernUi.field(this@KnoxZtManagerActivity, "Signer SHA-256 (comma separated)")
                addView(url)
                addView(apkSha)
                addView(signerSha)
                addView(ModernUi.action(this@KnoxZtManagerActivity, "Save trusted source", primary = true) { saveSource() })
            }
        )

        body.addView(ModernUi.section(this, "Recovery & inventory"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.action(this@KnoxZtManagerActivity, "Detect / enable / restore / install") { ensureReady() })
                addView(ModernUi.action(this@KnoxZtManagerActivity, "Prepare + list KnoxZT activities") { listActivities() })
            }
        )

        body.addView(ModernUi.section(this, "Result"))
        output = TextView(this).apply { text = "Ready"; setTextIsSelectable(true) }
        body.addView(ModernUi.outputCard(this, output))
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun saveSource() {
        runCatching {
            val source = KnoxZtInstallSource(
                url = url.text.toString().trim(),
                apkSha256 = apkSha.text.toString().trim(),
                signerSha256 = signerSha.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            )
            KnoxZtInstallSourceStore(this).save(source)
            output.text = "Trusted KnoxZT source saved"
        }.onFailure { output.text = "Source rejected: ${it.message}" }
    }

    private fun beginOperation(): Boolean = synchronized(operationLock) {
        if (operationRunning) return@synchronized false
        operationRunning = true
        true
    }

    private fun finishOperation() = synchronized(operationLock) {
        operationRunning = false
    }

    private fun postResultIfAlive(text: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            output.text = text
        }
    }

    private fun ensureReady() {
        if (!beginOperation()) {
            output.text = "KnoxZT operation already running"
            return
        }
        output.text = "Checking KnoxZT..."
        Thread {
            try {
                val result = KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = ShizukuUserServiceClient(this)).ensureReady()
                postResultIfAlive("status=${result.status}\ndetail=${result.detail}\nroutes=${result.attemptedRoutes}")
            } catch (error: Exception) {
                postResultIfAlive("KnoxZT recovery error\n${error.message ?: error.javaClass.simpleName}")
            } finally {
                finishOperation()
            }
        }.start()
    }

    private fun listActivities() {
        if (!beginOperation()) {
            output.text = "KnoxZT operation already running"
            return
        }
        output.text = "Preparing KnoxZT..."
        Thread {
            try {
                val (recovery, activities) = KnoxZtActivitySupport(this, AioDeviceAdminReceiver.componentName(this))
                    .prepareAndList(Process.myUserHandle())
                val text = buildString {
                    appendLine("recovery=${recovery.status} ${recovery.detail}")
                    appendLine("activities=${activities.size}")
                    activities.forEach { appendLine("${it.className} exported=${it.exported} enabled=${it.enabled} permission=${it.requiredPermission}") }
                }
                postResultIfAlive(text)
            } catch (error: Exception) {
                postResultIfAlive("KnoxZT activity inventory error\n${error.message ?: error.javaClass.simpleName}")
            } finally {
                finishOperation()
            }
        }.start()
    }
}
