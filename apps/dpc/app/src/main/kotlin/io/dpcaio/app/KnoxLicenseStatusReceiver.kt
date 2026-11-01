package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.dpcaio.knox.license.KnoxLicenseResultInterpreter
import io.dpcaio.knox.license.KnoxLicenseState

class KnoxLicenseStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KNOX_LICENSE_STATUS) return

        val managerClass = runCatching {
            Class.forName(KNOX_MANAGER_CLASS, false, context.classLoader)
        }.getOrNull() ?: return

        val statusExtra = stringConstant(managerClass, "EXTRA_LICENSE_STATUS") ?: return
        val errorExtra = stringConstant(managerClass, "EXTRA_LICENSE_ERROR_CODE") ?: return
        val statusText = intent.getStringExtra(statusExtra)
        val errorCode = intent.getIntExtra(errorExtra, if (statusText.equals("success", ignoreCase = true)) 0 else -1)

        val normalized = if (errorCode >= 0) {
            when (KnoxLicenseResultInterpreter().fromErrorCode(errorCode)) {
                KnoxLicenseState.ACTIVE -> KnoxLicenseActivationStatus.ACTIVE
                KnoxLicenseState.OFFLINE_PENDING,
                KnoxLicenseState.UNKNOWN,
                KnoxLicenseState.ACTIVATING -> KnoxLicenseActivationStatus.RETRYABLE
                KnoxLicenseState.FAILED_BINDING,
                KnoxLicenseState.FAILED_TERMINATED,
                KnoxLicenseState.FAILED -> KnoxLicenseActivationStatus.FAILED
            }
        } else {
            KnoxLicenseActivationStatus.FAILED
        }

        KnoxLicenseRuntimeStore(context).write(
            KnoxLicenseActivationSnapshot(
                status = normalized,
                detail = when (normalized) {
                    KnoxLicenseActivationStatus.ACTIVE -> "KPE_ACTIVE"
                    KnoxLicenseActivationStatus.RETRYABLE -> "KPE_RETRYABLE"
                    KnoxLicenseActivationStatus.FAILED -> "KPE_FAILED"
                    KnoxLicenseActivationStatus.UNSUPPORTED -> "KPE_UNSUPPORTED"
                },
                errorCode = errorCode.takeIf { it >= 0 },
            )
        )
    }

    private fun stringConstant(clazz: Class<*>, fieldName: String): String? =
        runCatching { clazz.getField(fieldName).get(null) as? String }.getOrNull()

    companion object {
        private const val KNOX_MANAGER_CLASS = "com.samsung.android.knox.license.KnoxEnterpriseLicenseManager"
        const val ACTION_KNOX_LICENSE_STATUS = "com.samsung.android.knox.intent.action.KNOX_LICENSE_STATUS"
    }
}
