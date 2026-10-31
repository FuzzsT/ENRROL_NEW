package io.dpcaio.app.engineering

import android.content.Context

data class SecretCodeReceiver(
    val action: String,
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
    val requiredPermission: String?,
)

class SamsungSecretCodeDiscovery(context: Context) {
    private val verifier = SecretCodeRuntimeVerifier(context)

    fun discover(rawCode: String): List<SecretCodeReceiver> {
        val code = SamsungSecretCodeCatalog.normalize(rawCode)
        if (code.isBlank()) return emptyList()
        return verifier.queryReceivers(code).map { receiver ->
            SecretCodeReceiver(
                action = receiver.action,
                packageName = receiver.packageName,
                className = receiver.className,
                enabled = receiver.enabled,
                exported = receiver.exported,
                requiredPermission = receiver.requiredPermission,
            )
        }
    }

    companion object {
        const val LEGACY_SECRET_CODE_ACTION = SecretCodeRuntimeVerifier.LEGACY_SECRET_CODE_ACTION
    }
}
