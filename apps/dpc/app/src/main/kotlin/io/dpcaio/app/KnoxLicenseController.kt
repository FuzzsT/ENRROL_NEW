package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import io.dpcaio.knox.license.KnoxLicenseResultInterpreter
import io.dpcaio.knox.license.KnoxLicenseState

enum class KnoxLicenseActivationStatus {
    ACTIVE,
    RETRYABLE,
    FAILED,
    UNSUPPORTED,
}

data class KnoxLicenseActivationSnapshot(
    val status: KnoxLicenseActivationStatus,
    val detail: String,
    val errorCode: Int? = null,
)

class KnoxLicenseRuntimeStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(snapshot: KnoxLicenseActivationSnapshot): KnoxLicenseActivationSnapshot {
        prefs.edit()
            .putString(KEY_STATUS, snapshot.status.name)
            .putString(KEY_DETAIL, snapshot.detail)
            .apply {
                if (snapshot.errorCode == null) remove(KEY_ERROR_CODE) else putInt(KEY_ERROR_CODE, snapshot.errorCode)
            }
            .apply()
        return snapshot
    }

    fun read(): KnoxLicenseActivationSnapshot? {
        val status = prefs.getString(KEY_STATUS, null)
            ?.let { runCatching { KnoxLicenseActivationStatus.valueOf(it) }.getOrNull() }
            ?: return null
        val errorCode = if (prefs.contains(KEY_ERROR_CODE)) prefs.getInt(KEY_ERROR_CODE, -1) else null
        return KnoxLicenseActivationSnapshot(
            status = status,
            detail = prefs.getString(KEY_DETAIL, "UNKNOWN") ?: "UNKNOWN",
            errorCode = errorCode,
        )
    }

    companion object {
        private const val PREFS = "dpc_knox_license_runtime"
        private const val KEY_STATUS = "status"
        private const val KEY_DETAIL = "detail"
        private const val KEY_ERROR_CODE = "errorCode"
    }
}

class KnoxLicenseController(private val context: Context) {
    private val runtimeStore = KnoxLicenseRuntimeStore(context)

    fun activateIfRequested(): KnoxLicenseActivationSnapshot {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.UNSUPPORTED, "NOT_SAMSUNG"))
        }
        if (!KnoxProvisioningSecretCapture.isAutoActivateRequested(context)) {
            return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.UNSUPPORTED, "AUTO_ACTIVATE_NOT_REQUESTED"))
        }

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.RETRYABLE, "WAIT_FOR_DEVICE_OWNER"))
        }

        val key = EnrollmentSecretStore(context)
            .get(KnoxProvisioningSecretCapture.KNOX_SECRET_REF)
            ?.knoxLicenseKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.FAILED, "KPE_KEY_MISSING"))

        val managerClass = runCatching { Class.forName(KNOX_MANAGER_CLASS, false, context.classLoader) }
            .getOrElse {
                return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.UNSUPPORTED, "KNOX_LICENSE_MANAGER_MISSING"))
            }

        return try {
            val manager = managerClass.getMethod("getInstance", Context::class.java).invoke(null, context)
                ?: return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.FAILED, "KNOX_LICENSE_MANAGER_NULL"))
            val methods = managerClass.methods.filter { it.name == "activateLicense" }
            val packageMethod = methods.firstOrNull { method ->
                val p = method.parameterTypes
                p.size == 2 && p[0] == String::class.java && p[1] == String::class.java
            }
            val simpleMethod = methods.firstOrNull { method ->
                val p = method.parameterTypes
                p.size == 1 && p[0] == String::class.java
            }
            when {
                packageMethod != null -> packageMethod.invoke(manager, key, context.packageName)
                simpleMethod != null -> simpleMethod.invoke(manager, key)
                else -> return runtimeStore.write(snapshot(KnoxLicenseActivationStatus.UNSUPPORTED, "ACTIVATE_LICENSE_METHOD_MISSING"))
            }
            runtimeStore.write(snapshot(KnoxLicenseActivationStatus.RETRYABLE, "ACTIVATION_REQUESTED"))
        } catch (_: ReflectiveOperationException) {
            runtimeStore.write(snapshot(KnoxLicenseActivationStatus.FAILED, "ACTIVATION_INVOCATION_FAILED"))
        } catch (_: RuntimeException) {
            runtimeStore.write(snapshot(KnoxLicenseActivationStatus.FAILED, "ACTIVATION_RUNTIME_FAILED"))
        }
    }

    companion object {
        const val KNOX_MANAGER_CLASS = "com.samsung.android.knox.license.KnoxEnterpriseLicenseManager"

        fun fromErrorCode(errorCode: Int): KnoxLicenseActivationStatus =
            when (KnoxLicenseResultInterpreter().fromErrorCode(errorCode)) {
                KnoxLicenseState.ACTIVE -> KnoxLicenseActivationStatus.ACTIVE
                KnoxLicenseState.OFFLINE_PENDING,
                KnoxLicenseState.UNKNOWN,
                KnoxLicenseState.ACTIVATING -> KnoxLicenseActivationStatus.RETRYABLE
                KnoxLicenseState.FAILED_BINDING,
                KnoxLicenseState.FAILED_TERMINATED,
                KnoxLicenseState.FAILED -> KnoxLicenseActivationStatus.FAILED
            }

        private fun snapshot(status: KnoxLicenseActivationStatus, detail: String, errorCode: Int? = null) =
            KnoxLicenseActivationSnapshot(status = status, detail = detail, errorCode = errorCode)
    }
}
