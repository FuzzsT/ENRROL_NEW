package io.dpcaio.app.engineering

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.dpcaio.engineering.SecretCodeDeclaration
import io.dpcaio.engineering.SecretCodeDeclarationKind
import io.dpcaio.engineering.SecretCodeExtractionResult
import io.dpcaio.engineering.SecretCodeManifestExtractor
import io.dpcaio.engineering.SecretCodeSourceKind
import java.nio.file.Files
import java.nio.file.Paths

data class SecretCodeInventoryItem(
    val packageName: String,
    val receiverClassName: String,
    val rawHost: String?,
    val normalizedCode: String?,
    val actions: Set<String>,
    val scheme: String?,
    val enabled: Boolean?,
    val exported: Boolean?,
    val requiredPermission: String?,
    val sourceKind: SecretCodeSourceKind,
    val sourceApkName: String,
    val declarationKind: SecretCodeDeclarationKind,
    val verification: SecretCodeVerification = SecretCodeVerification.DECLARED_ONLY,
)

data class SecretCodeScanProgress(
    val scannedPackages: Int,
    val totalPackages: Int,
    val candidates: Int,
)

sealed interface SecretCodeScanEvent {
    data class Candidate(val item: SecretCodeInventoryItem) : SecretCodeScanEvent
    data class Progress(val progress: SecretCodeScanProgress) : SecretCodeScanEvent
    data class Diagnostic(
        val packageName: String?,
        val sourceApkName: String?,
        val message: String,
    ) : SecretCodeScanEvent
    data object Cancelled : SecretCodeScanEvent
}

class InstalledPackageSecretCodeScanner(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun scan(
        cancelled: () -> Boolean = { false },
        onProgress: (SecretCodeScanProgress) -> Unit = {},
    ): Sequence<SecretCodeScanEvent> = sequence {
        val applications = try {
            installedApplications()
        } catch (error: RuntimeException) {
            yield(SecretCodeScanEvent.Diagnostic(null, null, error.message ?: "package enumeration failed"))
            return@sequence
        }

        var scannedPackages = 0
        var candidates = 0
        for (app in applications) {
            if (cancelled()) {
                yield(SecretCodeScanEvent.Cancelled)
                return@sequence
            }

            val sources = buildList {
                add(Triple(app.sourceDir, SecretCodeSourceKind.BASE_APK, app.sourceDir?.substringAfterLast('/')))
                app.splitSourceDirs.orEmpty().forEach { split ->
                    add(Triple(split, SecretCodeSourceKind.SPLIT_APK, split.substringAfterLast('/')))
                }
            }

            try {
                for ((pathText, sourceKind, sourceName) in sources) {
                    if (cancelled()) {
                        yield(SecretCodeScanEvent.Cancelled)
                        return@sequence
                    }
                    if (pathText.isNullOrBlank()) continue
                    val path = Paths.get(pathText)
                    if (!Files.isReadable(path)) {
                        yield(SecretCodeScanEvent.Diagnostic(app.packageName, sourceName, "APK path is not readable"))
                        continue
                    }

                    when (val result = SecretCodeManifestExtractor.extract(path, sourceKind)) {
                        is SecretCodeExtractionResult.Success -> {
                            for (declaration in result.declarations) {
                                val item = declaration.toInventoryItem(app.packageName)
                                candidates++
                                yield(SecretCodeScanEvent.Candidate(item))
                            }
                        }
                        is SecretCodeExtractionResult.Failure -> {
                            yield(
                                SecretCodeScanEvent.Diagnostic(
                                    packageName = app.packageName,
                                    sourceApkName = sourceName,
                                    message = "${result.error}: ${result.message}",
                                )
                            )
                        }
                    }
                }
            } catch (error: RuntimeException) {
                yield(
                    SecretCodeScanEvent.Diagnostic(
                        packageName = app.packageName,
                        sourceApkName = null,
                        message = error.message ?: "package scan failed",
                    )
                )
            }

            scannedPackages++
            val progress = SecretCodeScanProgress(scannedPackages, applications.size, candidates)
            onProgress(progress)
            yield(SecretCodeScanEvent.Progress(progress))
        }
    }

    private fun installedApplications(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
        }

    private fun SecretCodeDeclaration.toInventoryItem(packageName: String): SecretCodeInventoryItem =
        SecretCodeInventoryItem(
            packageName = packageName,
            receiverClassName = receiverClassName,
            rawHost = rawHost,
            normalizedCode = normalizedCode,
            actions = actions,
            scheme = scheme,
            enabled = enabled,
            exported = exported,
            requiredPermission = requiredPermission,
            sourceKind = sourceKind,
            sourceApkName = sourceApkName,
            declarationKind = declarationKind,
            verification = if (declarationKind == SecretCodeDeclarationKind.PATTERN_ONLY) {
                SecretCodeVerification.PATTERN_ONLY
            } else {
                SecretCodeVerification.DECLARED_ONLY
            },
        )
}
