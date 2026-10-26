package io.dpcaio.app

import android.content.Context
import org.json.JSONObject

data class EnrollmentDiagnosticsSnapshot(
    val sessionId: String?,
    val source: String?,
    val stage: String?,
    val requestedMode: String?,
    val policyProfile: String?,
    val serverUri: String?,
    val tokenFingerprint: String?,
    val retryCount: Int,
    val lastError: String?,
    val lastSuccessfulStage: String?,
    val reservationPresent: Boolean,
    val secretPresent: Boolean,
) {
    fun toJson(): String = JSONObject().apply {
        putNullable("sessionId", sessionId?.let(::redactId))
        putNullable("source", source)
        putNullable("stage", stage)
        putNullable("requestedMode", requestedMode)
        putNullable("policyProfile", policyProfile)
        putNullable("serverUri", serverUri)
        putNullable("tokenFingerprint", tokenFingerprint)
        put("retryCount", retryCount)
        putNullable("lastError", lastError)
        putNullable("lastSuccessfulStage", lastSuccessfulStage)
        put("reservationPresent", reservationPresent)
        put("secretPresent", secretPresent)
    }.toString(2)

    companion object {
        fun capture(context: Context): EnrollmentDiagnosticsSnapshot {
            val session = EnrollmentSessionStore(context).read()
            val secrets = session?.secretRef?.let { EnrollmentSecretStore(context).get(it) }
            return EnrollmentDiagnosticsSnapshot(
                sessionId = session?.sessionId,
                source = session?.source?.name,
                stage = session?.stage?.name,
                requestedMode = session?.requestedMode,
                policyProfile = session?.policyProfile,
                serverUri = session?.serverUri,
                tokenFingerprint = session?.tokenFingerprint,
                retryCount = session?.retryCount ?: 0,
                lastError = session?.lastError?.name,
                lastSuccessfulStage = session?.lastSuccessfulStage?.name,
                reservationPresent = !session?.reservationId.isNullOrBlank(),
                secretPresent = secrets?.enrollmentToken != null || secrets?.password != null,
            )
        }

        private fun redactId(value: String): String = if (value.length <= 12) value else value.take(8) + "…" + value.takeLast(4)
        private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    }
}
