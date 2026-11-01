package io.dpcaio.app

import android.content.Context
import android.os.PersistableBundle

object KnoxProvisioningSecretCapture {
    const val KEY_KNOX_LICENSE = "knox.licenseKey"
    const val KEY_KNOX_AUTO_ACTIVATE = "knox.autoActivate"
    const val KNOX_SECRET_REF = "knox-kpe-provisioning"

    private const val PREFS = "dpc_knox_provisioning"
    private const val PREF_AUTO_ACTIVATE = "autoActivate"

    fun capture(context: Context, extras: PersistableBundle?) {
        val key = extras?.getString(KEY_KNOX_LICENSE)?.trim()?.takeIf { it.isNotBlank() }
        if (key != null) {
            EnrollmentSecretStore(context).put(
                KNOX_SECRET_REF,
                EnrollmentSecrets(knoxLicenseKey = key),
            )
        }

        val autoActivate = extras?.getBoolean(KEY_KNOX_AUTO_ACTIVATE, false) ?: false
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AUTO_ACTIVATE, autoActivate)
            .apply()
    }

    fun isAutoActivateRequested(context: Context): Boolean =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_ACTIVATE, false)
}
