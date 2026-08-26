package io.dpcaio.offline

import java.security.MessageDigest

enum class OfflinePackageIssueCode { MISSING_FILE, HASH_MISMATCH }

data class OfflinePackageIssue(
    val code: OfflinePackageIssueCode,
    val packageName: String,
    val path: String,
    val detail: String
)

data class OfflinePackagePlan(
    val ready: Boolean,
    val verifiedFiles: List<String> = emptyList(),
    val issues: List<OfflinePackageIssue> = emptyList(),
    val blockingCode: String? = null
)

class OfflinePackagePlanner {
    fun plan(manifest: OfflineBundleManifest, availableFiles: Map<String, ByteArray>): OfflinePackagePlan {
        val verified = mutableListOf<String>()
        val issues = mutableListOf<OfflinePackageIssue>()
        for (pkg in manifest.packages.sortedBy { it.packageName }) {
            for (file in pkg.files) {
                val bytes = availableFiles[file.path]
                if (bytes == null) {
                    if (file.required) {
                        val code = if (file.path == pkg.baseFile) "OFFLINE_PACKAGE_MISSING:${pkg.packageName}" else "OFFLINE_SPLIT_MISSING:${file.path}"
                        issues += OfflinePackageIssue(OfflinePackageIssueCode.MISSING_FILE, pkg.packageName, file.path, code)
                    }
                    continue
                }
                if (file.sha256.isNotBlank() && sha256(bytes) != file.sha256.lowercase()) {
                    issues += OfflinePackageIssue(OfflinePackageIssueCode.HASH_MISMATCH, pkg.packageName, file.path, "OFFLINE_PACKAGE_HASH_MISMATCH:${file.path}")
                } else {
                    verified += file.path
                }
            }
        }
        val firstBlocking = issues.firstOrNull()?.detail
        return OfflinePackagePlan(issues.isEmpty(), verified.sorted(), issues, firstBlocking)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
