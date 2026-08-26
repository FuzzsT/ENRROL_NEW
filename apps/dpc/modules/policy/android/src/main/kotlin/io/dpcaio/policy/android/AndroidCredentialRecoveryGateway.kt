package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import io.dpcaio.policy.CredentialRecoveryEvidence
import io.dpcaio.policy.CredentialRecoveryPlanner
import io.dpcaio.policy.CredentialRecoverySnapshot
import io.dpcaio.policy.CredentialRecoveryState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.platform.AndroidUserId
import java.security.SecureRandom

class AndroidCredentialRecoveryGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val vault = CredentialRecoveryVault(appContext)
    private val planner = CredentialRecoveryPlanner()
    private val metadata = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("dpc_credential_recovery_state_v2", Context.MODE_PRIVATE)
    private val userId = AndroidUserId.fromUid(Process.myUid())
    private val scope = "u$userId:${admin.flattenToShortString()}"

    fun snapshot(): CredentialRecoverySnapshot {
        val supported = platformSupported()
        val provisioned = metadata.getBoolean(scopeKey("provisioned"), false)
        val revoked = metadata.getBoolean(scopeKey("revoked"), false)
        val rotating = metadata.getBoolean(scopeKey("rotating"), false)
        val stored = vault.contains(scope)
        val active = if (supported && provisioned) runCatching { dpm.isResetPasswordTokenActive(admin) }.getOrDefault(false) else false
        val state = planner.state(CredentialRecoveryEvidence(provisioned, active, stored, revoked, supported, rotating))
        return CredentialRecoverySnapshot(
            state = state,
            userId = userId,
            adminId = admin.flattenToShortString(),
            tokenStoredEncrypted = stored,
            tokenActive = active,
            detail = state.name,
        )
    }

    fun provision(): PolicyResult<CredentialRecoverySnapshot> {
        if (!platformSupported()) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Reset password token requires API 26+ secure-lock support")
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return try {
            val accepted = dpm.setResetPasswordToken(admin, token)
            if (!accepted) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "RESET_TOKEN_REJECTED")
            vault.put(scope, token)
            metadata.edit()
                .putBoolean(scopeKey("provisioned"), true)
                .putBoolean(scopeKey("revoked"), false)
                .putBoolean(scopeKey("rotating"), false)
                .commit()
            PolicyResult.success(snapshot())
        } catch (e: SecurityException) {
            PolicyResult.failure(PolicyStatus.SECURITY_EXCEPTION, "RESET_TOKEN_SECURITY_EXCEPTION", e.javaClass.name)
        } catch (e: RuntimeException) {
            PolicyResult.failure(PolicyStatus.FAILED, "RESET_TOKEN_PROVISION_FAILED:${e.javaClass.simpleName}", e.javaClass.name)
        } finally {
            token.fill(0)
        }
    }

    fun rotate(): PolicyResult<CredentialRecoverySnapshot> {
        if (!platformSupported()) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Reset password token requires API 26+")
        metadata.edit().putBoolean(scopeKey("rotating"), true).commit()
        val cleared = runCatching { dpm.clearResetPasswordToken(admin) }.getOrDefault(false)
        if (!cleared) {
            metadata.edit().putBoolean(scopeKey("rotating"), false).commit()
            return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "RESET_TOKEN_ROTATE_CLEAR_FAILED")
        }
        vault.remove(scope)
        metadata.edit().putBoolean(scopeKey("provisioned"), false).putBoolean(scopeKey("revoked"), false).commit()
        return provision()
    }

    fun revoke(): PolicyResult<CredentialRecoverySnapshot> {
        if (!platformSupported()) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Reset password token requires API 26+")
        return try {
            val cleared = dpm.clearResetPasswordToken(admin)
            if (!cleared) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "RESET_TOKEN_REVOKE_FAILED")
            vault.remove(scope)
            metadata.edit()
                .putBoolean(scopeKey("provisioned"), false)
                .putBoolean(scopeKey("revoked"), true)
                .putBoolean(scopeKey("rotating"), false)
                .commit()
            PolicyResult.success(snapshot())
        } catch (e: SecurityException) {
            PolicyResult.failure(PolicyStatus.SECURITY_EXCEPTION, "RESET_TOKEN_REVOKE_SECURITY_EXCEPTION", e.javaClass.name)
        }
    }

    fun resetCredential(newCredential: CharArray, flags: Int = 0): PolicyResult<Boolean> {
        if (!platformSupported()) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Reset password token requires API 26+")
        if (snapshot().state != CredentialRecoveryState.ACTIVE) return PolicyResult.failure(PolicyStatus.NOT_AUTHORIZED, "RESET_TOKEN_NOT_ACTIVE")
        val token = vault.get(scope) ?: return PolicyResult.failure(PolicyStatus.FAILED, "RESET_TOKEN_LOST")
        return try {
            val success = dpm.resetPasswordWithToken(admin, String(newCredential), token, flags)
            if (success) PolicyResult.success(true) else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "PASSWORD_RESET_REJECTED")
        } catch (e: SecurityException) {
            PolicyResult.failure(PolicyStatus.SECURITY_EXCEPTION, "PASSWORD_RESET_SECURITY_EXCEPTION", e.javaClass.name)
        } catch (e: RuntimeException) {
            PolicyResult.failure(PolicyStatus.FAILED, "PASSWORD_RESET_FAILED:${e.javaClass.simpleName}", e.javaClass.name)
        } finally {
            token.fill(0)
            newCredential.fill('\u0000')
        }
    }

    private fun platformSupported(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        appContext.packageManager.hasSystemFeature("android.hardware.secure_lock_screen")

    private fun scopeKey(name: String): String = "$scope:$name"
}
