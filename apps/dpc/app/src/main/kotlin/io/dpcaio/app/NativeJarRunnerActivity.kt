package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.security.MessageDigest

class NativeJarRunnerActivity : Activity() {
    private val manager by lazy { JarCallManager(this) }
    private lateinit var mainClassField: EditText
    private lateinit var argsField: EditText
    private lateinit var timeoutField: EditText
    private lateinit var trustedShaField: EditText
    private lateinit var selectedJarText: TextView
    private lateinit var output: TextView
    private var selectedJar: File? = null
    private var selectedSha256: String? = null
    private var importInProgress = false
    private var executionInProgress = false

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
                subtitle = "Run prefix-restricted DPC-AIO JAR entry points through app_process with an independently pinned SHA-256 and no arbitrary shell.",
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
                    text = selectedJar?.let { "${it.name}\nObserved SHA-256: ${selectedSha256 ?: "calculating"}" } ?: "No JAR selected"
                    setTextIsSelectable(true)
                }
                addView(selectedJarText)
                addView(ModernUi.action(this@NativeJarRunnerActivity, "Choose JAR", primary = true) { chooseJar() })
            }
        )

        body.addView(ModernUi.section(this, "Entry point"))
        body.addView(
            ModernUi.card(this).apply {
                trustedShaField = ModernUi.field(this@NativeJarRunnerActivity, "Trusted SHA-256 from release/source (64 hex)")
                mainClassField = ModernUi.field(this@NativeJarRunnerActivity, "Main class, e.g. io.dpcaio.jar.DiagnosticsMain")
                argsField = ModernUi.field(this@NativeJarRunnerActivity, "Arguments — one argument per line").apply {
                    setSingleLine(false)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 3
                    maxLines = 8
                    isVerticalScrollBarEnabled = true
                }
                timeoutField = ModernUi.field(this@NativeJarRunnerActivity, "Timeout seconds (1-120)")
                timeoutField.setText("30")
                addView(trustedShaField)
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
        output = ModernUi.outputCard(this, if (enabled) "Ready — verify the displayed hash against a trusted release/source before running" else "Enable guarded native JAR calling in Settings first")
        output.setTextIsSelectable(true)
        body.addView(output)
        setContentView(ModernUi.scrollPage(this, body))
    }

    private fun chooseJar() {
        if (importInProgress || executionInProgress || NativeJarOperationCoordinator.isBusy()) {
            output.text = "Another native JAR operation is already in progress"
            return
        }
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
        if (importInProgress || executionInProgress) {
            output.text = if (executionInProgress) "JAR execution is already in progress" else "A JAR import is already in progress"
            return
        }
        val importReservation = NativeJarOperationCoordinator.tryStartImport()
        if (importReservation == null) {
            output.text = "Another native JAR operation is already in progress"
            return
        }
        importInProgress = true
        output.text = "Importing JAR…"
        val worker = Thread {
            val result = try {
                runCatching {
                    val root = manager.privateJarDirectory()
                    val temp = File(root, "selected.jar.part")
                    val target = File(root, "selected.jar")
                    temp.delete()
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output ->
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
                        require(temp.length() > 0L) { "Selected JAR is empty" }
                        val digest = sha256(temp)
                        if (target.exists()) require(target.delete()) { "Cannot replace previous JAR" }
                        require(temp.renameTo(target)) { "Cannot commit imported JAR" }
                        target to digest
                    } catch (error: Throwable) {
                        temp.delete()
                        throw error
                    }
                }
            } finally {
                NativeJarOperationCoordinator.finishImport(importReservation)
            }
            runOnUiThread {
                importInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (file, digest) ->
                    selectedJar = file
                    selectedSha256 = digest
                    selectedJarText.text = "${file.name}\nObserved SHA-256: $digest"
                    output.text = "JAR imported. Compare the observed SHA-256 with a trusted release/source and enter that trusted value before running."
                }.onFailure { error ->
                    output.text = "Import failed: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
        try {
            worker.start()
        } catch (error: Throwable) {
            NativeJarOperationCoordinator.finishImport(importReservation)
            importInProgress = false
            output.text = "Import failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun runJar() {
        if (importInProgress || executionInProgress) {
            output.text = if (executionInProgress) "JAR execution is already in progress" else "Wait for the current JAR import to finish"
            return
        }
        val jar = selectedJar
        val observedDigest = selectedSha256
        if (jar == null || observedDigest == null) {
            output.text = "Choose a JAR first"
            return
        }
        val trustedDigest = trustedShaField.text.toString().trim().lowercase()
        if (!SHA256.matches(trustedDigest)) {
            output.text = "Enter a trusted SHA-256 (64 lowercase/uppercase hex characters) from an independent release/source"
            return
        }
        if (trustedDigest != observedDigest) {
            output.text = "SHA-256 mismatch — the selected JAR does not match the trusted digest"
            return
        }
        val mainClass = mainClassField.text.toString().trim()
        val arguments = argsField.text.toString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val timeout = timeoutField.text.toString().trim().toLongOrNull() ?: 30L
        val executionReservation = NativeJarOperationCoordinator.tryStartExecution()
        if (executionReservation == null) {
            output.text = "Another native JAR operation is already in progress"
            return
        }
        executionInProgress = true
        output.text = "Running…"
        val request = JarCallRequest(
            jarFile = jar,
            mainClass = mainClass,
            arguments = arguments,
            expectedSha256 = trustedDigest,
            timeoutSeconds = timeout,
        )
        val worker = Thread {
            val result = manager.executeReserved(request, executionReservation)
            runOnUiThread {
                executionInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                output.text = buildString {
                    appendLine(result.reason)
                    appendLine("started=${result.started} exit=${result.exitCode ?: "—"} timeout=${result.timedOut}")
                    if (result.stdout.isNotBlank()) appendLine("\nSTDOUT\n${result.stdout}")
                    if (result.stderr.isNotBlank()) appendLine("\nSTDERR\n${result.stderr}")
                }.trim()
            }
        }
        try {
            worker.start()
        } catch (error: Throwable) {
            NativeJarOperationCoordinator.finishExecution(executionReservation)
            executionInProgress = false
            output.text = "Cannot start JAR worker: ${error.message ?: error.javaClass.simpleName}"
        }
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
        private val SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}
