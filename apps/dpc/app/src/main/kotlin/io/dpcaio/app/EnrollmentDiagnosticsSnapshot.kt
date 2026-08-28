package io.dpcaio.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

enum class EnrollmentSessionDiagnosticState {
    ABSENT,
    READABLE,
    CORRUPT,
}

enum class ManagementDiagnosticState {
    UNMANAGED,
    DEVICE_OWNER,
    PROFILE_OWNER,
    ORGANIZATION_OWNED_PROFILE,
}

data class EnrollmentDiagnosticsSnapshot(
    val sessionState: EnrollmentSessionDiagnosticState,
    val managementState: ManagementDiagnosticState,
    val dpcVersion: String,
    val getProvisioningModeHandlerReady: Boolean,
    val policyComplianceHandlerReady: Boolean,
    val platformProvisioningHandlersReady: Boolean,
    val recommendedAction: String,
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
    val sessionReadErrorClass: String?,
) {
    fun toJson(): String = JSONObject().apply {
        put("sessionState", sessionState.name)
        put("managementState", managementState.name)
        put("dpcVersion", dpcVersion)
        put("getProvisioningModeHandlerReady", getProvisioningModeHandlerReady)
        put("policyComplianceHandlerReady", policyComplianceHandlerReady)
        put("platformProvisioningHandlersReady", platformProvisioningHandlersReady)
        put("recommendedAction", recommendedAction)
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
        putNullable("sessionReadErrorClass", sessionReadErrorClass)
    }.toString(2)

    companion object {
        private const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        private const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"

        fun capture(context: Context): EnrollmentDiagnosticsSnapshot {
            val readResult = EnrollmentSessionStore(context).readResult()
            val session = (readResult as? EnrollmentSessionReadResult.Present)?.session
            val secrets = session?.secretRef?.let { EnrollmentSecretStore(context).get(it) }
            val managementState = managementState(context)
            val getProvisioningModeHandlerReady = handlerReady(
                context,
                ACTION_GET_PROVISIONING_MODE,
                ProvisioningModeActivity::class.java.name,
            )
            val policyComplianceHandlerReady = handlerReady(
                context,
                ACTION_ADMIN_POLICY_COMPLIANCE,
                PolicyComplianceActivity::class.java.name,
            )
            val platformProvisioningHandlersReady =
                getProvisioningModeHandlerReady && policyComplianceHandlerReady
            val sessionState = when (readResult) {
                EnrollmentSessionReadResult.Absent -> EnrollmentSessionDiagnosticState.ABSENT
                is EnrollmentSessionReadResult.Present -> EnrollmentSessionDiagnosticState.READABLE
                is EnrollmentSessionReadResult.Corrupt -> EnrollmentSessionDiagnosticState.CORRUPT
            }
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")

            return EnrollmentDiagnosticsSnapshot(
                sessionState = sessionState,
                managementState = managementState,
                dpcVersion = version,
                getProvisioningModeHandlerReady = getProvisioningModeHandlerReady,
                policyComplianceHandlerReady = policyComplianceHandlerReady,
                platformProvisioningHandlersReady = platformProvisioningHandlersReady,
                recommendedAction = recommendedAction(
                    sessionState = sessionState,
                    managementState = managementState,
                    handlersReady = platformProvisioningHandlersReady,
                ),
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
                sessionReadErrorClass = (readResult as? EnrollmentSessionReadResult.Corrupt)?.errorClass,
            )
        }

        private fun managementState(context: Context): ManagementDiagnosticState {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val packageName = context.packageName
            if (dpm.isDeviceOwnerApp(packageName)) return ManagementDiagnosticState.DEVICE_OWNER
            if (!dpm.isProfileOwnerApp(packageName)) return ManagementDiagnosticState.UNMANAGED
            val organizationOwned = if (Build.VERSION.SDK_INT >= 30) {
                runCatching { dpm.isOrganizationOwnedDeviceWithManagedProfile }.getOrDefault(false)
            } else {
                false
            }
            return if (organizationOwned) {
                ManagementDiagnosticState.ORGANIZATION_OWNED_PROFILE
            } else {
                ManagementDiagnosticState.PROFILE_OWNER
            }
        }

        @Suppress("DEPRECATION")
        private fun handlerReady(context: Context, action: String, expectedClassName: String): Boolean {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            return context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .any { resolveInfo ->
                    val info = resolveInfo.activityInfo
                    info.name == expectedClassName && info.enabled && info.exported
                }
        }

        private fun recommendedAction(
            sessionState: EnrollmentSessionDiagnosticState,
            managementState: ManagementDiagnosticState,
            handlersReady: Boolean,
        ): String = when {
            !handlersReady ->
                "Provisioning handlers are not resolvable; verify the installed variant and manifest before retrying enrollment."
            sessionState == EnrollmentSessionDiagnosticState.CORRUPT ->
                "Export diagnostics and start a fresh supported provisioning flow; the stored enrollment session cannot be decoded."
            managementState == ManagementDiagnosticState.UNMANAGED && sessionState == EnrollmentSessionDiagnosticState.ABSENT ->
                "Start Android Enterprise provisioning (QR, KME, zero-touch, NFC, or BYOD work profile). Installing the APK alone does not create Device Owner or Profile Owner."
            managementState == ManagementDiagnosticState.UNMANAGED ->
                "The enrollment session exists but this app is not Device Owner/Profile Owner; resume or repeat the supported provisioning flow."
            sessionState == EnrollmentSessionDiagnosticState.ABSENT ->
                "Ownership is active but no enrollment session is stored; export diagnostics before starting a new enrollment session."
            else ->
                "Ownership and enrollment session are present; inspect stage and lastError before using Retry."
        }

        private fun redactId(value: String): String = if (value.length <= 12) value else value.take(8) + "…" + value.takeLast(4)
        private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    }
}
