package io.dpcaio.app.engineering

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager

data class SecretCodeReceiver(
    val action: String,
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
    val requiredPermission: String?,
)

class SamsungSecretCodeDiscovery(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun discover(rawCode: String): List<SecretCodeReceiver> {
        val code = SamsungSecretCodeCatalog.normalize(rawCode)
        if (code.isBlank()) return emptyList()
        val data = Uri.parse("android_secret_code://${Uri.encode(code)}")
        val actions = listOf(
            TelephonyManager.ACTION_SECRET_CODE,
            LEGACY_SECRET_CODE_ACTION,
        )
        return actions.flatMap { action ->
            queryBroadcastReceivers(Intent(action, data)).mapNotNull { resolve ->
                resolve.activityInfo?.let { info ->
                    SecretCodeReceiver(
                        action = action,
                        packageName = info.packageName,
                        className = info.name,
                        enabled = info.enabled,
                        exported = info.exported,
                        requiredPermission = info.permission,
                    )
                }
            }
        }.distinctBy { Triple(it.action, it.packageName, it.className) }
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

    companion object {
        const val LEGACY_SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
    }
}
