package io.dpcaio.installer.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.installer.ApkArtifactMetadata
import io.dpcaio.installer.ApkPlusArchivePlanner
import io.dpcaio.installer.ApkPlusEntry
import io.dpcaio.installer.ApkPlusReject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile


data class ApkPlusStagedFile(
    val archivePath: String,
    val file: File,
    val sha256: String,
)

data class ApkPlusStageResult(
    val accepted: Boolean,
    val rejections: Set<ApkPlusReject>,
    val stagedFiles: List<ApkPlusStagedFile>,
    val detail: String,
)

class AndroidApkPlusStager(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val planner = ApkPlusArchivePlanner()

    fun stage(archive: File): ApkPlusStageResult {
        if (!archive.isFile) return ApkPlusStageResult(false, setOf(ApkPlusReject.ARCHIVE_INVALID), emptyList(), "ARCHIVE_NOT_FOUND")
        val unixModes = runCatching { ZipCentralDirectoryAttributes.readUnixModes(archive) }.getOrElse {
            return ApkPlusStageResult(false, setOf(ApkPlusReject.ARCHIVE_INVALID), emptyList(), "CENTRAL_DIRECTORY_INVALID:${it.javaClass.simpleName}")
        }
        val plan = runCatching {
            ZipFile(archive).use { zip ->
                val entries = buildList {
                    val it = zip.entries()
                    while (it.hasMoreElements()) {
                        val e = it.nextElement()
                        add(ApkPlusEntry(e.name, e.compressedSize, e.size, unixModes[e.name] ?: 0, e.isDirectory))
                    }
                }
                planner.plan(entries)
            }
        }.getOrElse {
            return ApkPlusStageResult(false, setOf(ApkPlusReject.ARCHIVE_INVALID), emptyList(), "ZIP_INVALID:${it.javaClass.simpleName}")
        }
        if (!plan.accepted) return ApkPlusStageResult(false, plan.rejections, emptyList(), "ARCHIVE_REJECTED")
        val baseApk = plan.baseApk
            ?: return ApkPlusStageResult(false, plan.rejections + ApkPlusReject.BASE_APK_MISSING, emptyList(), "ARCHIVE_REJECTED")

        val staging = File(appContext.cacheDir, "apk-plus-staging/${System.nanoTime()}").apply { mkdirs() }
        val staged = mutableListOf<ApkPlusStagedFile>()
        val metadata = mutableListOf<ApkArtifactMetadata>()
        try {
            ZipFile(archive).use { zip ->
                for ((index, path) in (listOf(baseApk) + plan.splitApks).withIndex()) {
                    val entry = zip.getEntry(path) ?: return rejectAndCleanup(staging, ApkPlusReject.ARCHIVE_INVALID, "MISSING_ENTRY:$path")
                    val target = File(staging, if (path == baseApk) "base.apk" else "split-$index.apk")
                    zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    val info = if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageArchiveInfo(target.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(target.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                    } ?: return rejectAndCleanup(staging, ApkPlusReject.ARCHIVE_INVALID, "APK_PARSE_FAILED:$path")
                    val signers = info.signingInfo?.apkContentsSigners.orEmpty().mapTo(linkedSetOf()) { digest(it.toByteArray()) }
                    metadata += ApkArtifactMetadata(
                        archivePath = path,
                        packageName = info.packageName,
                        versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
                        signerSha256 = signers,
                        base = path == baseApk,
                    )
                    staged += ApkPlusStagedFile(path, target, sha256(target))
                }
            }
            val metadataRejections = planner.verifyApkMetadata(metadata)
            if (metadataRejections.isNotEmpty()) {
                staging.deleteRecursively()
                return ApkPlusStageResult(false, metadataRejections, emptyList(), "APK_METADATA_REJECTED")
            }
            return ApkPlusStageResult(true, emptySet(), staged, "STAGED_VERIFIED")
        } catch (t: Throwable) {
            staging.deleteRecursively()
            return ApkPlusStageResult(false, setOf(ApkPlusReject.ARCHIVE_INVALID), emptyList(), "STAGING_FAILED:${t.javaClass.simpleName}")
        }
    }

    private fun rejectAndCleanup(root: File, rejection: ApkPlusReject, detail: String): ApkPlusStageResult {
        root.deleteRecursively()
        return ApkPlusStageResult(false, setOf(rejection), emptyList(), detail)
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

/** Minimal ZIP central-directory parser used only to read Unix file mode bits. ZIP64 is rejected. */
object ZipCentralDirectoryAttributes {
    private const val EOCD = 0x06054b50
    private const val CEN = 0x02014b50

    fun readUnixModes(file: File): Map<String, Int> {
        RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            val scanLength = minOf(fileLength, 65557L).toInt()
            val start = fileLength - scanLength
            raf.seek(start)
            val tail = ByteArray(scanLength)
            raf.readFully(tail)
            var eocd = -1
            for (i in tail.size - 22 downTo 0) {
                if (u32(tail, i) == EOCD.toLong()) { eocd = i; break }
            }
            require(eocd >= 0) { "EOCD_NOT_FOUND" }
            val entries = u16(tail, eocd + 10)
            val cdSize = u32(tail, eocd + 12)
            val cdOffset = u32(tail, eocd + 16)
            require(entries != 0xFFFF && cdSize != 0xFFFFFFFFL && cdOffset != 0xFFFFFFFFL) { "ZIP64_UNSUPPORTED" }
            require(cdOffset + cdSize <= fileLength) { "CENTRAL_DIRECTORY_RANGE" }
            raf.seek(cdOffset)
            val result = linkedMapOf<String, Int>()
            repeat(entries) {
                val header = ByteArray(46)
                raf.readFully(header)
                require(u32(header, 0) == CEN.toLong()) { "CENTRAL_SIGNATURE" }
                val versionMadeBy = u16(header, 4)
                val host = versionMadeBy ushr 8
                val nameLen = u16(header, 28)
                val extraLen = u16(header, 30)
                val commentLen = u16(header, 32)
                val external = u32(header, 38)
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8)
                val unixMode = if (host == 3) ((external ushr 16) and 0xFFFF).toInt() else 0
                result[name] = unixMode
                if (extraLen > 0) raf.skipBytes(extraLen)
                if (commentLen > 0) raf.skipBytes(commentLen)
            }
            return result
        }
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}
