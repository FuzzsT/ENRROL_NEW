package io.dpcaio.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import io.dpcaio.core.model.EnrollmentSession

class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val allowed = intent.getIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
        ).orEmpty()
        val incomingExtras = adminExtras()
        val allowOfflineRequested = intent.getBooleanExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE,
            false,
        )
        val normalizedExtras = PersistableBundle(incomingExtras ?: PersistableBundle()).apply {
            if (intent.hasExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE)) {
                putBoolean(EnrollmentConfigParser.KEY_ALLOW_OFFLINE, allowOfflineRequested)
            }
        }
        val parsedEnrollment = EnrollmentConfigParser.parse(normalizedExtras)
        val requestedMode = normalizedExtras.getString(ProvisioningModeSelector.EXTRA_REQUESTED_MODE)
            ?: parsedEnrollment.config.requestedMode
        val mode = ProvisioningModeSelector.select(
            requestedMode = requestedMode,
            allowedModes = allowed,
            fullyManagedMode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            managedProfileMode = DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE,
        ) ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val sessionStore = EnrollmentSessionStore(this)
        val secretStore = EnrollmentSecretStore(this)
        val session = sessionStore.read()?.copy(
            requestedMode = parsedEnrollment.config.requestedMode,
            allowOffline = parsedEnrollment.config.allowOffline,
            offlineMode = parsedEnrollment.config.offlineMode,
            offlineBundleId = parsedEnrollment.config.offlineBundleId,
        )?.also(sessionStore::write) ?: EnrollmentSession.new(parsedEnrollment.config).let { initial ->
            val secretRef = if (!parsedEnrollment.config.enrollmentToken.isNullOrBlank() || !parsedEnrollment.config.password.isNullOrBlank()) {
                "session:${initial.sessionId}"
            } else null
            if (secretRef != null) secretStore.put(
                secretRef,
                EnrollmentSecrets(parsedEnrollment.config.enrollmentToken, parsedEnrollment.config.password),
            )
            initial.copy(
                tokenFingerprint = EnrollmentSessionStore.tokenFingerprint(parsedEnrollment.config.enrollmentToken),
                secretRef = secretRef,
            ).also(sessionStore::write)
        }
        val outgoingExtras = PersistableBundle(normalizedExtras).apply {
            putString(EnrollmentCoordinator.KEY_SESSION_ID, session.sessionId)
            putString(EnrollmentConfigParser.KEY_SOURCE, parsedEnrollment.config.source.name)
            putString(EnrollmentConfigParser.KEY_OFFLINE_MODE, parsedEnrollment.config.offlineMode)
            parsedEnrollment.config.offlineBundleId?.let { putString(EnrollmentConfigParser.KEY_OFFLINE_BUNDLE_ID, it) }
        }
        val result = Intent().apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, outgoingExtras)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun adminExtras(): PersistableBundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }
}
