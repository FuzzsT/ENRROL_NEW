package io.dpcaio.appmanager.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.dpcaio.appmanager.PackageExportFormat
import io.dpcaio.appmanager.PackageExportPlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PackageExportResult(
    val format: PackageExportFormat,
    val sha256ByEntry: Map<String, String>,
    val obbIncluded: Boolean,
)

class AndroidPackageExporter(context: Context) {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    fun export(plan: PackageExportPlan, destinationUri: Uri): PackageExportResult {
        require(destinationUri != Uri.EMPTY) { "destinationUri must not be empty" }
        return when (plan.format) {
            PackageExportFormat.APK -> exportApk(plan, destinationUri)
            PackageExportFormat.APKS,
            PackageExportFormat.XAPK -> exportArchive(plan, destinationUri)
        }
    }

    private fun exportApk(plan: PackageExportPlan, destinationUri: Uri): PackageExportResult {
        require(plan.entries.size == 1) { "APK export requires exactly one base entry" }
        val entry = plan.entries.single()
        PackageExportPlan.requireSafeEntryName(entry.entryName)
        val source = requireReadableFile(entry.sourcePath)
        val output = requireNotNull(contentResolver.openOutputStream(destinationUri, "wt")) {
            "Unable to open SAF destination for APK export"
        }
        val digest = output.use { out ->
            BufferedOutputStream(out).use { buffered ->
                copyWithSha256(source, buffered)
            }
        }
        return PackageExportResult(
            format = plan.format,
            sha256ByEntry = mapOf(entry.entryName to digest),
            obbIncluded = false,
        )
    }

    private fun exportArchive(plan: PackageExportPlan, destinationUri: Uri): PackageExportResult {
        val output = requireNotNull(contentResolver.openOutputStream(destinationUri, "wt")) {
            "Unable to open SAF destination for package archive export"
        }
        val hashes = linkedMapOf<String, String>()
        output.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                plan.entries.forEach { entry ->
                    PackageExportPlan.requireSafeEntryName(entry.entryName)
                    val source = requireReadableFile(entry.sourcePath)
                    zip.putNextEntry(ZipEntry(entry.entryName))
                    hashes[entry.entryName] = copyWithSha256(source, zip)
                    zip.closeEntry()
                }

                // Generated diagnostics are metadata only. They never copy another app's private data.
                plan.generatedTextEntries.toSortedMap().forEach { (entryName, text) ->
                    hashes[entryName] = writeGeneratedEntry(zip, entryName, text)
                }

                val manifestJson = buildManifestJson(plan, hashes)
                writeGeneratedEntry(zip, plan.manifestEntryName, manifestJson)
                val sums = hashes.entries.joinToString(separator = "\n", postfix = "\n") { (name, hash) ->
                    "$hash  $name"
                }
                writeGeneratedEntry(zip, plan.checksumsEntryName, sums)
            }
        }
        return PackageExportResult(
            format = plan.format,
            sha256ByEntry = hashes.toMap(),
            obbIncluded = plan.obbIncluded,
        )
    }

    private fun buildManifestJson(plan: PackageExportPlan, hashes: Map<String, String>): String {
        val entries = JSONArray()
        hashes.forEach { (name, hash) ->
            entries.put(
                JSONObject()
                    .put("name", name)
                    .put("sha256", hash)
                    .put("generated", name in plan.generatedTextEntries)
            )
        }
        return JSONObject()
            .put("schemaVersion", 2)
            .put("format", plan.format.name)
            .put("archiveKind", plan.archiveKind)
            .put("packageName", plan.packageName)
            .put("versionName", plan.versionName ?: JSONObject.NULL)
            .put("versionCode", plan.versionCode)
            .put("obbIncluded", plan.obbIncluded)
            .put("entries", entries)
            .toString(2)
    }

    private fun writeGeneratedEntry(zip: ZipOutputStream, entryName: String, text: String): String {
        PackageExportPlan.requireSafeEntryName(entryName)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        zip.putNextEntry(ZipEntry(entryName))
        bytes.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun requireReadableFile(path: String): File {
        val file = File(path)
        require(file.isFile && file.canRead()) { "Package artifact is not readable: $path" }
        return file
    }

    private fun copyWithSha256(source: File, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(source).use { raw ->
            BufferedInputStream(raw).use { buffered ->
                DigestInputStream(buffered, digest).use { input ->
                    input.copyTo(output)
                }
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
