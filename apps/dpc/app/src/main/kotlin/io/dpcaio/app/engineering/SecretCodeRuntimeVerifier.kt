package io.dpcaio.app.engineering

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import io.dpcaio.engineering.SecretCodeDeclarationKind

enum class SecretCodeVerification {
    VERIFIED,
    DECLARED_ONLY,
    DISABLED,
    PERMISSION_GATED,
    NOT_EXPORTED,
    NOT_VISIBLE,
    PATTERN_ONLY,
    PACKAGE_GONE,
    ERROR,
}

data class RuntimeSecretCodeReceiver(
    val action: String,
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
    val requiredPermission: String?,
)

class SecretCodeRuntimeVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun verify(item: SecretCodeInventoryItem): SecretCodeInventoryItem {
        if (item.declarationKind == SecretCodeDeclarationKind.PATTERN_ONLY) {
            return item.copy(verification = SecretCodeVerification.PATTERN_ONLY)
        }
        val code = item.normalizedCode?.takeIf { it.isNotBlank() }
            ?: return item.copy(verification = SecretCodeVerification.DECLARED_ONLY)

        return try {
            if (!packageExists(item.packageName)) {
                item.copy(verification = SecretCodeVerification.PACKAGE_GONE)
            } else {
                val canonical = canonicalClassName(item.packageName, item.receiverClassName)
                val live = queryReceivers(code, item.packageName).filter { receiver ->
                    receiver.packageName == item.packageName &&
                        canonicalClassName(receiver.packageName, receiver.className) == canonical &&
                        receiver.action in item.actions
                }
                val first = live.firstOrNull()
                val verification = when {
                    live.any { !it.enabled } || item.enabled == false -> SecretCodeVerification.DISABLED
                    live.any { !it.exported } || item.exported == false -> SecretCodeVerification.NOT_EXPORTED
                    live.any { !it.requiredPermission.isNullOrBlank() } || !item.requiredPermission.isNullOrBlank() ->
                        SecretCodeVerification.PERMISSION_GATED
                    first != null -> SecretCodeVerification.VERIFIED
                    else -> SecretCodeVerification.NOT_VISIBLE
                }
                item.copy(
                    verification = verification,
                    enabled = first?.enabled ?: item.enabled,
                    exported = first?.exported ?: item.exported,
                    requiredPermission = first?.requiredPermission ?: item.requiredPermission,
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            item.copy(verification = SecretCodeVerification.PACKAGE_GONE)
        } catch (_: RuntimeException) {
            item.copy(verification = SecretCodeVerification.ERROR)
        }
    }

    fun queryReceivers(rawCode: String, packageName: String? = null): List<RuntimeSecretCodeReceiver> {
        val code = SamsungSecretCodeCatalog.normalize(rawCode)
        if (code.isBlank()) return emptyList()
        val data = Uri.parse("android_secret_code://${Uri.encode(code)}")
        return listOf(TelephonyManager.ACTION_SECRET_CODE, LEGACY_SECRET_CODE_ACTION)
            .flatMap { action ->
                val intent = Intent(action, data).apply {
                    if (!packageName.isNullOrBlank()) setPackage(packageName)
                }
                queryBroadcastReceivers(intent).mapNotNull { resolve ->
                    resolve.activityInfo?.let { info ->
                        RuntimeSecretCodeReceiver(
                            action = action,
                            packageName = info.packageName,
                            className = info.name,
                            enabled = info.enabled,
                            exported = info.exported,
                            requiredPermission = info.permission,
                        )
                    }
                }
            }
            .distinctBy { Triple(it.action, it.packageName, canonicalClassName(it.packageName, it.className)) }
    }

    private fun packageExists(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        return true
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

    private fun canonicalClassName(packageName: String, className: String): String = when {
        className.startsWith('.') -> packageName + className
        '.' !in className -> "$packageName.$className"
        else -> className
    }

    companion object {
        const val LEGACY_SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
    }
}
