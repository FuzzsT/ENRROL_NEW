package io.dpcaio.installer

import java.nio.file.Paths


enum class ApkPlusReject {
    ARCHIVE_INVALID,
    ARCHIVE_TOO_LARGE,
    PATH_TRAVERSAL,
    DUPLICATE_CANONICAL_PATH,
    SYMLINK_ENTRY,
    ENTRY_COUNT_EXCEEDED,
    SINGLE_ENTRY_TOO_LARGE,
    COMPRESSION_RATIO_EXCEEDED,
    BASE_APK_MISSING,
    MULTIPLE_BASE_APKS,
    UNSUPPORTED_HELPER_ENTRY,
    PACKAGE_NAME_MISMATCH,
    VERSION_MISMATCH,
    SIGNER_MISMATCH,
}

data class ApkPlusArchiveLimits(
    val maxEntries: Int = 256,
    val maxTotalUncompressedBytes: Long = 1024L * 1024 * 1024,
    val maxSingleEntryBytes: Long = 512L * 1024 * 1024,
    val maxCompressionRatio: Double = 100.0,
)

data class ApkPlusEntry(
    val path: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val unixMode: Int = 0,
    val directory: Boolean = false,
)

data class ApkPlusArchivePlan(
    val accepted: Boolean,
    val baseApk: String?,
    val splitApks: List<String>,
    val metadataPath: String?,
    val rejections: Set<ApkPlusReject>,
    val canonicalPaths: List<String>,
)

data class ApkArtifactMetadata(
    val archivePath: String,
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
    val base: Boolean,
)

class ApkPlusArchivePlanner(
    private val limits: ApkPlusArchiveLimits = ApkPlusArchiveLimits(),
) {
    fun plan(entries: List<ApkPlusEntry>): ApkPlusArchivePlan {
        val rejects = linkedSetOf<ApkPlusReject>()
        if (entries.size > limits.maxEntries) rejects += ApkPlusReject.ENTRY_COUNT_EXCEEDED
        var total = 0L
        val canonical = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        for (entry in entries) {
            val normalized = canonicalize(entry.path)
            if (normalized == null) {
                rejects += ApkPlusReject.PATH_TRAVERSAL
                continue
            }
            if (!seen.add(normalized)) rejects += ApkPlusReject.DUPLICATE_CANONICAL_PATH
            canonical += normalized
            if (entry.directory) continue
            if ((entry.unixMode and 0xF000) == 0xA000) rejects += ApkPlusReject.SYMLINK_ENTRY
            if (entry.uncompressedSize < 0 || entry.compressedSize < 0) rejects += ApkPlusReject.ARCHIVE_INVALID
            if (entry.uncompressedSize > limits.maxSingleEntryBytes) rejects += ApkPlusReject.SINGLE_ENTRY_TOO_LARGE
            total = safeAdd(total, entry.uncompressedSize, rejects)
            if (entry.uncompressedSize > 0) {
                if (entry.compressedSize == 0L || entry.uncompressedSize.toDouble() / entry.compressedSize.coerceAtLeast(1).toDouble() > limits.maxCompressionRatio) {
                    rejects += ApkPlusReject.COMPRESSION_RATIO_EXCEEDED
                }
            }
            if (isHelperExecutable(normalized)) rejects += ApkPlusReject.UNSUPPORTED_HELPER_ENTRY
        }
        if (total > limits.maxTotalUncompressedBytes) rejects += ApkPlusReject.ARCHIVE_TOO_LARGE

        val apks = canonical.filter { it.endsWith(".apk", ignoreCase = true) }
        val bases = apks.filter { it.substringAfterLast('/').equals("base.apk", ignoreCase = true) }
        if (bases.isEmpty()) rejects += ApkPlusReject.BASE_APK_MISSING
        if (bases.size > 1) rejects += ApkPlusReject.MULTIPLE_BASE_APKS
        val base = bases.singleOrNull()
        val splits = apks.filterNot { it == base }.sorted()
        val metadata = canonical.firstOrNull { it.substringAfterLast('/').equals("apk+.json", ignoreCase = true) }
        return ApkPlusArchivePlan(rejects.isEmpty(), base, splits, metadata, rejects, canonical)
    }

    fun verifyApkMetadata(items: List<ApkArtifactMetadata>): Set<ApkPlusReject> {
        val rejects = linkedSetOf<ApkPlusReject>()
        val base = items.singleOrNull { it.base } ?: return setOf(ApkPlusReject.BASE_APK_MISSING)
        for (item in items) {
            if (item.packageName != base.packageName) rejects += ApkPlusReject.PACKAGE_NAME_MISMATCH
            if (item.versionCode != base.versionCode) rejects += ApkPlusReject.VERSION_MISMATCH
            if (item.signerSha256.normalized() != base.signerSha256.normalized()) rejects += ApkPlusReject.SIGNER_MISMATCH
        }
        return rejects
    }

    private fun canonicalize(path: String): String? {
        if (path.isBlank() || '\u0000' in path) return null
        val slash = path.replace('\\', '/')
        if (slash.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(slash)) return null
        val segments = slash.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null
        if (segments.isEmpty()) return null
        return segments.joinToString("/")
    }

    private fun safeAdd(current: Long, add: Long, rejects: MutableSet<ApkPlusReject>): Long {
        return try { Math.addExact(current, add) } catch (_: ArithmeticException) {
            rejects += ApkPlusReject.ARCHIVE_TOO_LARGE
            Long.MAX_VALUE
        }
    }

    private fun isHelperExecutable(path: String): Boolean {
        val lower = path.lowercase()
        if (lower.substringAfterLast('/') == "apk+.json") return false
        return lower.endsWith(".sh") || lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".cmd") ||
            lower.endsWith(".dex") || lower.endsWith(".jar") || lower.endsWith(".so") || lower.endsWith(".zip")
    }

    private fun Set<String>.normalized(): Set<String> = mapTo(linkedSetOf()) { it.replace(":", "").trim().uppercase() }
}
