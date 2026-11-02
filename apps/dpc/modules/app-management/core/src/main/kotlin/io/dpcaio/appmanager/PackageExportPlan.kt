package io.dpcaio.appmanager

import java.io.File

enum class PackageExportFormat {
    APK,
    APKS,
    XAPK,
    AIO_BUNDLE,
}

data class InstalledPackageArtifacts(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val baseApk: String,
    val splitApks: List<String> = emptyList(),
    val obbFiles: List<String> = emptyList(),
)

data class PackageExportEntry(
    val sourcePath: String,
    val entryName: String,
)

data class PackageExportPlan(
    val format: PackageExportFormat,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val entries: List<PackageExportEntry>,
    val generatedTextEntries: Map<String, String> = emptyMap(),
    val manifestEntryName: String = "manifest.json",
    val checksumsEntryName: String = "SHA256SUMS.txt",
    val obbIncluded: Boolean = false,
) {
    companion object {
        const val PROCESS_SNAPSHOT_ENTRY = "process-snapshot.json"

        fun forFormat(
            format: PackageExportFormat,
            artifacts: InstalledPackageArtifacts,
            generatedTextEntries: Map<String, String> = emptyMap(),
        ): PackageExportPlan {
            require(artifacts.packageName.isNotBlank()) { "packageName must not be blank" }
            require(artifacts.baseApk.isNotBlank()) { "baseApk must not be blank" }

            val entries = when (format) {
                PackageExportFormat.APK -> listOf(
                    PackageExportEntry(artifacts.baseApk, "base.apk")
                )

                PackageExportFormat.APKS -> buildArchiveEntries(artifacts, includeObb = false)
                PackageExportFormat.XAPK,
                PackageExportFormat.AIO_BUNDLE -> buildArchiveEntries(artifacts, includeObb = true)
            }

            entries.forEach { requireSafeEntryName(it.entryName) }
            generatedTextEntries.keys.forEach(::requireSafeEntryName)
            requireSafeEntryName("manifest.json")
            requireSafeEntryName("SHA256SUMS.txt")

            val allNames = entries.map { it.entryName } + generatedTextEntries.keys
            require(allNames.distinct().size == allNames.size) {
                "duplicate archive entry names are not allowed"
            }
            require("manifest.json" !in allNames && "SHA256SUMS.txt" !in allNames) {
                "generated entries must not replace exporter metadata"
            }
            if (format == PackageExportFormat.AIO_BUNDLE) {
                require(PROCESS_SNAPSHOT_ENTRY in generatedTextEntries) {
                    "AIO_BUNDLE requires process-snapshot.json"
                }
            }

            return PackageExportPlan(
                format = format,
                packageName = artifacts.packageName,
                versionName = artifacts.versionName,
                versionCode = artifacts.versionCode,
                entries = entries,
                generatedTextEntries = generatedTextEntries.toMap(),
                obbIncluded = format in setOf(PackageExportFormat.XAPK, PackageExportFormat.AIO_BUNDLE) &&
                    artifacts.obbFiles.isNotEmpty(),
            )
        }

        fun forFormat(artifacts: InstalledPackageArtifacts, format: PackageExportFormat): PackageExportPlan =
            forFormat(format, artifacts)

        private fun buildArchiveEntries(
            artifacts: InstalledPackageArtifacts,
            includeObb: Boolean,
        ): List<PackageExportEntry> {
            val result = mutableListOf(PackageExportEntry(artifacts.baseApk, "base.apk"))
            artifacts.splitApks
                .sortedBy { File(it).name }
                .forEachIndexed { index, path ->
                    val rawName = File(path).name.removeSuffix(".apk")
                    val safeStem = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "split_$index" }
                    result += PackageExportEntry(path, "splits/${index.toString().padStart(3, '0')}_$safeStem.apk")
                }
            if (includeObb) {
                artifacts.obbFiles
                    .sortedBy { File(it).name }
                    .forEachIndexed { index, path ->
                        val rawName = File(path).name
                        val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "obb_$index.obb" }
                        result += PackageExportEntry(path, "Android/obb/${artifacts.packageName}/$safeName")
                    }
            }
            return result
        }

        fun requireSafeEntryName(entryName: String) {
            require(entryName.isNotBlank()) { "entryName must not be blank" }
            require(!entryName.startsWith('/')) { "absolute archive entry is not allowed" }
            require('\\' !in entryName) { "backslash archive entry is not allowed" }
            val parts = entryName.split('/')
            require(parts.none { it.isBlank() || it == "." || it == ".." }) {
                "archive traversal entry is not allowed"
            }
        }
    }
}
