package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "KnoxZT Framework"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingDp(20,20,20,20) }
        output = TextView(this).apply { setTextIsSelectable(true) }
        url = EditText(this).apply { hint = "Trusted HTTPS APK URL" }
        apkSha = EditText(this).apply { hint = "APK SHA-256" }
        signerSha = EditText(this).apply { hint = "Signer SHA-256 (comma separated)" }
        val save = Button(this).apply { text = "Save trusted source"; setOnClickListener { saveSource() } }
        val ensure = Button(this).apply { text = "Detect / enable / restore / install"; setOnClickListener { ensureReady() } }
        val activities = Button(this).apply { text = "Prepare + list KnoxZT activities"; setOnClickListener { listActivities() } }
        root.addView(url); root.addView(apkSha); root.addView(signerSha); root.addView(save); root.addView(ensure); root.addView(activities)
        root.addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        DpcUiShell.install(this, root)
        setContentView(root)
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

    private fun ensureReady() {
        output.text = "Checking KnoxZT..."
        Thread {
            val result = KnoxZtRecoveryManager(this, AioDeviceAdminReceiver.componentName(this), shizuku = ShizukuUserServiceClient(this)).ensureReady()
            runOnUiThread { output.text = "status=${result.status}\ndetail=${result.detail}\nroutes=${result.attemptedRoutes}" }
        }.start()
    }

    private fun listActivities() {
        output.text = "Preparing KnoxZT..."
        Thread {
            val (recovery, activities) = KnoxZtActivitySupport(this, AioDeviceAdminReceiver.componentName(this))
                .prepareAndList(Process.myUserHandle())
            val text = buildString {
                appendLine("recovery=${recovery.status} ${recovery.detail}")
                appendLine("activities=${activities.size}")
                activities.forEach { appendLine("${it.className} exported=${it.exported} enabled=${it.enabled} permission=${it.requiredPermission}") }
            }
            runOnUiThread { output.text = text }
        }.start()
    }
}
