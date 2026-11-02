package io.dpcaio.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class JarCallRequest(
    val jarFile: File,
    val mainClass: String,
    val arguments: List<String> = emptyList(),
    val expectedSha256: String,
    val timeoutSeconds: Long = 30L,
)

data class JarCallResult(
    val started: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val reason: String,
)

class JarCallManager(private val context: Context) {
    private val app = context.applicationContext

    fun execute(request: JarCallRequest): JarCallResult {
        if (!DpcUiPreferences.read(app).nativeJarCallingEnabled) {
            return rejected("JAR calling is disabled in Settings")
        }
        val native = NativeSupportManager.probe(app)
        val appProcess = native.appProcessPath ?: return rejected("app_process is not available")
        val jar = validateJar(request.jarFile) ?: return rejected("JAR must be inside the app private jar directory")
        if (!MAIN_CLASS.matches(request.mainClass)) return rejected("Invalid main class")
        if (!request.mainClass.startsWith(ALLOWED_MAIN_CLASS_PREFIX)) {
            return rejected("Main class is outside the DPC-AIO allowlist")
        }
        val expected = request.expectedSha256.trim().lowercase()
        if (!SHA256.matches(expected)) return rejected("Expected SHA-256 is required")
        val actual = sha256(jar)
        if (actual != expected) return rejected("JAR SHA-256 mismatch")
        if (request.arguments.size > MAX_ARGS || request.arguments.any { it.length > MAX_ARG_LENGTH }) {
            return rejected("JAR argument limits exceeded")
        }

        val command = mutableListOf(appProcess, "/system/bin", request.mainClass)
        command += request.arguments
        val process = try {
            ProcessBuilder(command)
                .directory(app.filesDir)
                .redirectErrorStream(false)
                .apply { environment()["CLASSPATH"] = jar.absolutePath }
                .start()
        } catch (error: Exception) {
            return rejected("Cannot start app_process: ${error.message ?: error.javaClass.simpleName}")
        }

        val finished = runCatching {
            process.waitFor(request.timeoutSeconds.coerceIn(1L, MAX_TIMEOUT_SECONDS), TimeUnit.SECONDS)
        }.getOrDefault(false)
        if (!finished) process.destroyForcibly()

        val stdout = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
            .take(MAX_OUTPUT_CHARS)
        val stderr = runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("")
            .take(MAX_OUTPUT_CHARS)
        val exit = if (finished) runCatching { process.exitValue() }.getOrNull() else null
        return JarCallResult(
            started = true,
            exitCode = exit,
            stdout = stdout,
            stderr = stderr,
            timedOut = !finished,
            reason = when {
                !finished -> "Timed out"
                exit == 0 -> "Completed"
                else -> "Process exited with code ${exit ?: "unknown"}"
            },
        )
    }

    fun privateJarDirectory(): File = File(app.filesDir, JAR_DIR).apply { mkdirs() }

    private fun validateJar(file: File): File? {
        val root = privateJarDirectory().canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (candidate.parentFile != root) return null
        if (!candidate.isFile || candidate.extension.lowercase() != "jar") return null
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rejected(reason: String) = JarCallResult(
        started = false,
        exitCode = null,
        stdout = "",
        stderr = "",
        timedOut = false,
        reason = reason,
    )

    companion object {
        private const val JAR_DIR = "native-jars"
        private const val ALLOWED_MAIN_CLASS_PREFIX = "io.dpcaio.jar."
        private const val MAX_ARGS = 32
        private const val MAX_ARG_LENGTH = 4096
        private const val MAX_TIMEOUT_SECONDS = 120L
        private const val MAX_OUTPUT_CHARS = 128 * 1024
        private val MAIN_CLASS = Regex("[A-Za-z_][A-Za-z0-9_$.]{0,199}")
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}
