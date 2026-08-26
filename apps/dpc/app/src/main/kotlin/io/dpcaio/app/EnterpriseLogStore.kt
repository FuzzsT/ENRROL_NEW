package io.dpcaio.app

import android.content.Context
import io.dpcaio.policy.EnterpriseLogBatch
import java.io.File

class EnterpriseLogStore(context: Context) {
    private val dir = File(context.filesDir, "enterprise-logs").apply { mkdirs() }

    fun append(batch: EnterpriseLogBatch): File {
        val target = File(dir, "%013d-%s.jsonl".format(batch.capturedAtEpochMs, batch.channel.name.lowercase()))
        target.bufferedWriter().use { out ->
            batch.payloadJsonLines.forEach { line -> out.appendLine(line) }
        }
        trim()
        return target
    }

    fun batches(): List<File> = dir.listFiles()?.filter { it.isFile && it.extension == "jsonl" }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    fun exportRedacted(source: File, destination: File): File {
        destination.parentFile?.mkdirs()
        destination.bufferedWriter().use { out ->
            source.useLines { lines -> lines.forEach { out.appendLine(redact(it)) } }
        }
        return destination
    }

    private fun redact(line: String): String = line
        .replace(IPV4, "<redacted-ip>")
        .replace(HOST_OR_PACKAGE, "<redacted-host-or-package>")

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "jsonl" }
            ?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        fun totalBytes() = files.sumOf { it.length() }
        while (files.size > MAX_BATCHES || totalBytes() > MAX_BYTES) {
            files.removeFirstOrNull()?.delete()
        }
    }

    companion object {
        const val MAX_BATCHES = 10
        const val MAX_BYTES = 5L * 1024L * 1024L
        private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
        private val HOST_OR_PACKAGE = Regex("\\b[a-zA-Z][a-zA-Z0-9_-]*(?:\\.[a-zA-Z0-9_-]+){2,}\\b")
    }
}
