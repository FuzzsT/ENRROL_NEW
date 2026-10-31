package io.dpcaio.app.engineering

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import io.dpcaio.engineering.SecretCodeDeclaration
import io.dpcaio.engineering.SecretCodeExtractionResult
import io.dpcaio.engineering.SecretCodeInventoryRecord
import io.dpcaio.engineering.SecretCodeInventoryReducer
import io.dpcaio.engineering.SecretCodeLiveReceiver
import io.dpcaio.engineering.SecretCodeManifestExtractor
import io.dpcaio.engineering.SecretCodeSourceKind
import java.nio.file.Paths

data class SecretCodeInventoryScanResult(
    val records: List<SecretCodeInventoryRecord>,
    val scannedPackages: Int,
    val extractionFailures: Int,
)

class SecretCodeInventoryScanner(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun scan(): SecretCodeInventoryScanResult {
        val applications = installedApplications()
        val records = mutableListOf<SecretCodeInventoryRecord>()
        var failures = 0

        applications.forEach { app ->
            val declarations = mutableListOf<SecretCodeDeclaration>()
            fun extract(path: String?, kind: SecretCodeSourceKind) {
                if (path.isNullOrBlank()) return
                when (val result = SecretCodeManifestExtractor.extract(Paths.get(path), kind)) {
                    is SecretCodeExtractionResult.Success -> declarations += result.declarations
                    is SecretCodeExtractionResult.Failure -> failures++
                }
            }

            extract(app.sourceDir, SecretCodeSourceKind.BASE_APK)
            app.splitSourceDirs.orEmpty().forEach { split ->
                extract(split, SecretCodeSourceKind.SPLIT_APK)
            }
            if (declarations.isEmpty()) return@forEach

            val liveReceivers = verifyLiveReceivers(app.packageName, declarations)
            records += SecretCodeInventoryReducer.reduce(
                packageName = app.packageName,
                declarations = declarations,
                liveReceivers = liveReceivers,
            )
        }

        return SecretCodeInventoryScanResult(
            records = records.sortedWith(
                compareBy<SecretCodeInventoryRecord> { it.packageName }
                    .thenBy { it.code ?: it.rawHost ?: "" }
                    .thenBy { it.receiverClassName }
            ),
            scannedPackages = applications.size,
            extractionFailures = failures,
        )
    }

    private fun verifyLiveReceivers(
        packageName: String,
        declarations: List<SecretCodeDeclaration>,
    ): List<SecretCodeLiveReceiver> {
        val live = linkedSetOf<SecretCodeLiveReceiver>()
        declarations.forEach { declaration ->
            val code = declaration.normalizedCode ?: return@forEach
            declaration.actions.forEach { action ->
                val data = Uri.parse("android_secret_code://${Uri.encode(code)}")
                val intent = Intent(action, data).setPackage(packageName)
                queryBroadcastReceivers(intent).forEach { resolve ->
                    resolve.activityInfo?.let { info ->
                        live += SecretCodeLiveReceiver(
                            packageName = info.packageName,
                            className = info.name,
                            action = action,
                            enabled = info.enabled,
                            exported = info.exported,
                            requiredPermission = info.permission,
                        )
                    }
                }
            }
        }
        return live.toList()
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

    private fun queryBroadcastReceivers(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
}
