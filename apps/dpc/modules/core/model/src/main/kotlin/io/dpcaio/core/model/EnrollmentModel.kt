package io.dpcaio.core.model

import java.util.UUID

enum class EnrollmentSource {
    QR,
    KME,
    ZERO_TOUCH,
    NFC,
    MANUAL_TOKEN,
    BYOD_WORK_PROFILE,
    GENERIC_ANDROID_ENTERPRISE,
}

enum class EnrollmentStage {
    RECEIVED,
    VALIDATING,
    NETWORK_CHECK,
    PROVISIONING_MODE,
    POLICY_COMPLIANCE,
    RESERVING,
    REGISTERING,
    BOOTSTRAP_VERIFY,
    APPLYING_PROFILE,
    POLICY_READBACK,
    COMMITTING,
    POST_PROVISION,
    LOCAL_PROVISIONED,
    SERVER_REGISTRATION_PENDING,
    COMPLETE,
    WAITING_FOR_RETRY,
    FAILED,
}

enum class EnrollmentErrorCode(val retryable: Boolean) {
    CONFIG_INVALID(false),
    NETWORK_UNAVAILABLE(true),
    SERVER_UNREACHABLE(true),
    TOKEN_INVALID(false),
    TOKEN_EXPIRED(false),
    TOKEN_ALREADY_USED(false),
    PROFILE_NOT_FOUND(false),
    MODE_NOT_ALLOWED(false),
    DEVICE_NOT_ELIGIBLE(false),
    RETRYABLE_SERVER_ERROR(true),
    TLS_ERROR(false),
    SIGNATURE_INVALID(false),
    BOOTSTRAP_EXPIRED(false),
    POLICY_INCOMPATIBLE(false),
    POLICY_APPLY_FAILED(true),
}

data class NormalizedEnrollmentConfig(
    val source: EnrollmentSource = EnrollmentSource.GENERIC_ANDROID_ENTERPRISE,
    val requestedMode: String = "work-profile",
    val enrollmentToken: String? = null,
    val policyProfile: String = "default",
    val serverUri: String? = null,
    val organizationId: String? = null,
    val username: String? = null,
    val password: String? = null,
    val kmeUri: String? = null,
    val allowOffline: Boolean = false,
    val offlineMode: String = "ONLINE",
    val offlineBundleId: String? = null,
    val rawSourceKeys: Set<String> = emptySet(),
)

data class EnrollmentSession(
    val sessionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: EnrollmentSource,
    val stage: EnrollmentStage,
    val requestedMode: String,
    val policyProfile: String,
    val serverUri: String?,
    val organizationId: String?,
    val allowOffline: Boolean = false,
    val offlineMode: String = "ONLINE",
    val offlineBundleId: String? = null,
    val tokenFingerprint: String? = null,
    val secretRef: String? = null,
    val reservationId: String? = null,
    val retryCount: Int = 0,
    val lastError: EnrollmentErrorCode? = null,
    val lastSuccessfulStage: EnrollmentStage? = null,
) {
    fun advance(next: EnrollmentStage, nowMillis: Long): EnrollmentSession = copy(
        stage = next,
        updatedAt = nowMillis,
        lastSuccessfulStage = if (next == EnrollmentStage.FAILED || next == EnrollmentStage.WAITING_FOR_RETRY) {
            lastSuccessfulStage
        } else {
            next
        },
        lastError = null,
    )

    fun fail(code: EnrollmentErrorCode, nowMillis: Long): EnrollmentSession = copy(
        stage = if (code.retryable) EnrollmentStage.WAITING_FOR_RETRY else EnrollmentStage.FAILED,
        updatedAt = nowMillis,
        retryCount = retryCount + 1,
        lastError = code,
    )

    companion object {
        fun new(config: NormalizedEnrollmentConfig, nowMillis: Long = System.currentTimeMillis()): EnrollmentSession = EnrollmentSession(
            sessionId = UUID.randomUUID().toString(),
            createdAt = nowMillis,
            updatedAt = nowMillis,
            source = config.source,
            stage = EnrollmentStage.RECEIVED,
            requestedMode = config.requestedMode,
            policyProfile = config.policyProfile,
            serverUri = config.serverUri,
            organizationId = config.organizationId,
            allowOffline = config.allowOffline,
            offlineMode = config.offlineMode,
            offlineBundleId = config.offlineBundleId,
        )
    }
}

object EnrollmentRetryPolicy {
    private val delays = listOf(5_000L, 15_000L, 30_000L, 120_000L)
    fun delayMillis(attempt: Int): Long? = if (attempt <= 0) null else delays.getOrNull(attempt - 1)
}
