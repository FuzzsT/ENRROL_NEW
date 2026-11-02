package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.security.MessageDigest

class NativeJarRunnerActivity : Activity() {
    private val manager by lazy { JarCallManager(this) }
    private lateinit var mainClassField: EditText
    private lateinit var argsField: EditText
    private lateinit var timeoutField: EditText
    private lateinit var selectedJarText: TextView
    private lateinit var output: TextView
    private var selectedJar: File? = null
    private var selectedSha256: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Native JAR Runner"
        render()
    }

    private fun render() {
        val native = NativeSupportManager.probe(this)
        val enabled = DpcUiPreferences.read(this).nativeJarCallingEnabled
        val body = ModernUi.page(this)

        body.addView(
            ModernUi.hero(
                this,
                title = "Native JAR Runner",
                subtitle = "Run allowlisted DPC-AIO JAR entry points through app_process without an arbitrary shell.",
                eyebrow = if (enabled) "Enabled" else "Disabled in Settings",
            )
        )
        body.addView(ModernUi.section(this, "Runtime"))
        body.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.detailRow(this@NativeJarRunnerActivity, "app_process", native.appProcessPath ?: "Unavailable"))
                addView(ModernUi.detailRow(this@NativeJarRunnerActivity, "Allowed class prefix", "io.dpcaio.jar.*"))
                addView(ModernUi.detailRow(this@NativeJarRunnerActivity, "JAR storage", manager.privateJarDirectory().absolutePath))
            }
        )

        body.addView(ModernUi.section(this, "JAR"))
        body.addView(
            ModernUi.card(this).apply {
                selectedJarText = TextView(this@NativeJarRunnerActivity).apply {
                    text = selectedJar?.let { "${it.name}\nSHA-256: ${selectedSha256 ?: "calculating"}" } ?: "No JAR selected"
                    setTextIsSelectable(true)
                }
                addView(selectedJarText)
                addView(ModernUi.action(this@NativeJarRunnerActivity, "Choose JAR", primary = true) { chooseJar() })
            }
        )

        body.addView(ModernUi.section(this, "Entry point"))
        body.addView(
            ModernUi.card(this).apply {
                mainClassField = ModernUi.field(this@NativeJarRunnerActivity, "Main class, e.g. io.dpcaio.jar.DiagnosticsMain")
                argsField = ModernUi.field(this@NativeJarRunnerActivity, "Arguments — one argument per line")
                argsField.minLines = 3
                timeoutField = ModernUi.field(this@NativeJarRunnerActivity, "Timeout seconds (1-120)")
                timeoutField.setText("30")
                addView(mainClassField)
                addView(argsField)
                addView(timeoutField)
                addView(
                    ModernUi.action(this@NativeJarRunnerActivity, "Run JAR", primary = true) { runJar() }.apply {
                        isEnabled = enabled && native.jarCallingAvailable
                    }
                )
            }
        )

        body.addView(ModernUi.section(this, "Result"))
        output = ModernUi.outputCard(this, if (enabled) "Ready" else "Enable guarded native JAR calling in Settings first")
        output.setTextIsSelectable(true)
        body.addView(output)
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun chooseJar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/java-archive", "application/octet-stream", "application/zip"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_JAR)
    }

    @Deprecated("Compatibility with Activity result API on classic Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_JAR || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importJar(uri)
    }

    private fun importJar(uri: Uri) {
        output.text = "Importing JAR…"
        Thread {
            runCatching {
                val target = File(manager.privateJarDirectory(), "selected.jar")
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            require(total <= MAX_JAR_BYTES) { "JAR exceeds 64 MB limit" }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("Cannot open selected document")
                require(target.length() > 0L) { "Selected JAR is empty" }
                val digest = sha256(target)
                target to digest
            }.onSuccess { (file, digest) ->
                runOnUiThread {
                    selectedJar = file
                    selectedSha256 = digest
                    selectedJarText.text = "${file.name}\nSHA-256: $digest"
                    output.text = "JAR imported into private app storage"
                }
            }.onFailure { error ->
                runOnUiThread { output.text = "Import failed: ${error.message ?: error.javaClass.simpleName}" }
            }
        }.start()
    }

    private fun runJar() {
        val jar = selectedJar
        val digest = selectedSha256
        if (jar == null || digest == null) {
            output.text = "Choose a JAR first"
            return
        }
        val mainClass = mainClassField.text.toString().trim()
        val arguments = argsField.text.toString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val timeout = timeoutField.text.toString().trim().toLongOrNull() ?: 30L
        output.text = "Running…"
        Thread {
            val result = manager.execute(
                JarCallRequest(
                    jarFile = jar,
                    mainClass = mainClass,
                    arguments = arguments,
                    expectedSha256 = digest,
                    timeoutSeconds = timeout,
                )
            )
            runOnUiThread {
                output.text = buildString {
                    appendLine(result.reason)
                    appendLine("started=${result.started} exit=${result.exitCode ?: "—"} timeout=${result.timedOut}")
                    if (result.stdout.isNotBlank()) appendLine("\nSTDOUT\n${result.stdout}")
                    if (result.stderr.isNotBlank()) appendLine("\nSTDERR\n${result.stderr}")
                }.trim()
            }
        }.start()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val REQUEST_JAR = 4107
        private const val MAX_JAR_BYTES = 64L * 1024L * 1024L
    }
}
