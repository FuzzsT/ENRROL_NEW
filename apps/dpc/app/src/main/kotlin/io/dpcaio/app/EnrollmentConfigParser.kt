package io.dpcaio.app

import android.os.PersistableBundle
import io.dpcaio.core.model.EnrollmentSource
import io.dpcaio.core.model.NormalizedEnrollmentConfig

data class ParsedEnrollmentConfig(
    val config: NormalizedEnrollmentConfig,
    val sourceWasExplicit: Boolean,
)

object EnrollmentConfigParser {
    const val KEY_SOURCE = "io.dpcaio.extra.ENROLLMENT_SOURCE"
    const val KEY_MODE = "io.dpcaio.extra.PROVISIONING_MODE"
    const val KEY_TOKEN = "enrollmentToken"
    const val KEY_POLICY_PROFILE = "policyProfile"
    const val KEY_SERVER_URI = "io.dpcaio.extra.ENROLLMENT_ENDPOINT"
    const val KEY_ORGANIZATION_ID = "organizationId"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_KME_URI = "kmeUri"
    const val KEY_ZERO_TOUCH = "zeroTouch"
    const val KEY_NFC = "nfcProvisioning"
    const val KEY_ALLOW_OFFLINE = "android.app.extra.PROVISIONING_ALLOW_OFFLINE"
    const val KEY_OFFLINE_MODE = "io.dpcaio.extra.ENROLLMENT_OFFLINE_MODE"
    const val KEY_OFFLINE_BUNDLE_ID = "io.dpcaio.extra.OFFLINE_BUNDLE_ID"
    private val VALID_OFFLINE_MODES = setOf("ONLINE", "ONLINE_PREFERRED", "FULL_OFFLINE", "OFFLINE_THEN_SYNC")

    fun parse(extras: PersistableBundle?, explicitSource: String? = null): ParsedEnrollmentConfig {
        val rawKeys = extras?.keySet()?.toSet().orEmpty()
        val explicit = explicitSource?.takeIf { it.isNotBlank() } ?: extras?.getString(KEY_SOURCE)?.takeIf { it.isNotBlank() }
        val source = explicit?.let(::parseSource) ?: detectSource(extras)
        return ParsedEnrollmentConfig(
            config = NormalizedEnrollmentConfig(
                source = source,
                requestedMode = extras?.getString(KEY_MODE)?.takeIf { it.isNotBlank() } ?: "work-profile",
                enrollmentToken = extras?.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() },
                policyProfile = extras?.getString(KEY_POLICY_PROFILE)?.takeIf { it.isNotBlank() } ?: "default",
                serverUri = extras?.getString(KEY_SERVER_URI)?.takeIf { it.isNotBlank() },
                organizationId = extras?.getString(KEY_ORGANIZATION_ID)?.takeIf { it.isNotBlank() },
                username = extras?.getString(KEY_USERNAME)?.takeIf { it.isNotBlank() },
                password = extras?.getString(KEY_PASSWORD)?.takeIf { it.isNotBlank() },
                kmeUri = extras?.getString(KEY_KME_URI)?.takeIf { it.isNotBlank() },
                allowOffline = extras?.getBoolean(KEY_ALLOW_OFFLINE, false) ?: false,
                offlineMode = normalizeOfflineMode(extras?.getString(KEY_OFFLINE_MODE), extras?.getBoolean(KEY_ALLOW_OFFLINE, false) ?: false),
                offlineBundleId = extras?.getString(KEY_OFFLINE_BUNDLE_ID)?.takeIf { it.isNotBlank() },
                rawSourceKeys = rawKeys,
            ),
            sourceWasExplicit = explicit != null,
        )
    }

    private fun normalizeOfflineMode(value: String?, frameworkAllowOffline: Boolean): String {
        val normalized = value?.trim()?.uppercase()?.takeIf { it in VALID_OFFLINE_MODES }
        return normalized ?: if (frameworkAllowOffline) "ONLINE_PREFERRED" else "ONLINE"
    }

    private fun parseSource(value: String): EnrollmentSource = when (value.trim().lowercase()) {
        "qr" -> EnrollmentSource.QR
        "kme", "knox-mobile-enrollment" -> EnrollmentSource.KME
        "zero-touch", "zero_touch", "zerotouch" -> EnrollmentSource.ZERO_TOUCH
        "nfc" -> EnrollmentSource.NFC
        "manual", "manual-token", "manual_token" -> EnrollmentSource.MANUAL_TOKEN
        "byod", "byod-work-profile" -> EnrollmentSource.BYOD_WORK_PROFILE
        else -> EnrollmentSource.GENERIC_ANDROID_ENTERPRISE
    }

    private fun detectSource(extras: PersistableBundle?): EnrollmentSource = when {
        extras?.getString(KEY_KME_URI).isNullOrBlank().not() -> EnrollmentSource.KME
        extras?.getBoolean(KEY_ZERO_TOUCH, false) == true -> EnrollmentSource.ZERO_TOUCH
        extras?.getBoolean(KEY_NFC, false) == true -> EnrollmentSource.NFC
        extras?.getString(KEY_TOKEN).isNullOrBlank().not() -> EnrollmentSource.QR
        else -> EnrollmentSource.GENERIC_ANDROID_ENTERPRISE
    }
}
