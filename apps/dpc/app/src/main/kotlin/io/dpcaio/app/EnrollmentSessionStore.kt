package io.dpcaio.app

import android.content.Context
import io.dpcaio.core.model.EnrollmentErrorCode
import io.dpcaio.core.model.EnrollmentSession
import io.dpcaio.core.model.EnrollmentSource
import io.dpcaio.core.model.EnrollmentStage
import java.security.MessageDigest

class EnrollmentSessionStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): EnrollmentSession? {
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        return runCatching {
            EnrollmentSession(
                sessionId = sessionId,
                createdAt = prefs.getLong(KEY_CREATED_AT, 0L),
                updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
                source = EnrollmentSource.valueOf(prefs.getString(KEY_SOURCE, EnrollmentSource.GENERIC_ANDROID_ENTERPRISE.name)!!),
                stage = EnrollmentStage.valueOf(prefs.getString(KEY_STAGE, EnrollmentStage.RECEIVED.name)!!),
                requestedMode = prefs.getString(KEY_REQUESTED_MODE, "work-profile") ?: "work-profile",
                policyProfile = prefs.getString(KEY_POLICY_PROFILE, "default") ?: "default",
                serverUri = prefs.getString(KEY_SERVER_URI, null),
                organizationId = prefs.getString(KEY_ORGANIZATION_ID, null),
                allowOffline = prefs.getBoolean(KEY_ALLOW_OFFLINE, false),
                offlineMode = prefs.getString(KEY_OFFLINE_MODE, "ONLINE") ?: "ONLINE",
                offlineBundleId = prefs.getString(KEY_OFFLINE_BUNDLE_ID, null),
                tokenFingerprint = prefs.getString(KEY_TOKEN_FINGERPRINT, null),
                secretRef = prefs.getString(KEY_SECRET_REF, null),
                reservationId = prefs.getString(KEY_RESERVATION_ID, null),
                retryCount = prefs.getInt(KEY_RETRY_COUNT, 0),
                lastError = prefs.getString(KEY_LAST_ERROR, null)?.let(EnrollmentErrorCode::valueOf),
                lastSuccessfulStage = prefs.getString(KEY_LAST_SUCCESSFUL_STAGE, null)?.let(EnrollmentStage::valueOf),
            )
        }.getOrNull()
    }

    fun write(session: EnrollmentSession) {
        prefs.edit()
            .putString(KEY_SESSION_ID, session.sessionId)
            .putLong(KEY_CREATED_AT, session.createdAt)
            .putLong(KEY_UPDATED_AT, session.updatedAt)
            .putString(KEY_SOURCE, session.source.name)
            .putString(KEY_STAGE, session.stage.name)
            .putString(KEY_REQUESTED_MODE, session.requestedMode)
            .putString(KEY_POLICY_PROFILE, session.policyProfile)
            .putString(KEY_SERVER_URI, session.serverUri)
            .putString(KEY_ORGANIZATION_ID, session.organizationId)
            .putBoolean(KEY_ALLOW_OFFLINE, session.allowOffline)
            .putString(KEY_OFFLINE_MODE, session.offlineMode)
            .putString(KEY_OFFLINE_BUNDLE_ID, session.offlineBundleId)
            .putString(KEY_TOKEN_FINGERPRINT, session.tokenFingerprint)
            .putString(KEY_SECRET_REF, session.secretRef)
            .putString(KEY_RESERVATION_ID, session.reservationId)
            .putInt(KEY_RETRY_COUNT, session.retryCount)
            .putString(KEY_LAST_ERROR, session.lastError?.name)
            .putString(KEY_LAST_SUCCESSFUL_STAGE, session.lastSuccessfulStage?.name)
            .commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS = "dpc_enrollment_session"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_SOURCE = "source"
        private const val KEY_STAGE = "stage"
        private const val KEY_REQUESTED_MODE = "requested_mode"
        private const val KEY_POLICY_PROFILE = "policy_profile"
        private const val KEY_SERVER_URI = "server_uri"
        private const val KEY_ORGANIZATION_ID = "organization_id"
        private const val KEY_ALLOW_OFFLINE = "allow_offline"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_OFFLINE_BUNDLE_ID = "offline_bundle_id"
        private const val KEY_TOKEN_FINGERPRINT = "token_fingerprint"
        private const val KEY_SECRET_REF = "secret_ref"
        private const val KEY_RESERVATION_ID = "reservation_id"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_SUCCESSFUL_STAGE = "last_successful_stage"

        fun tokenFingerprint(token: String?): String? {
            if (token.isNullOrBlank()) return null
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}
