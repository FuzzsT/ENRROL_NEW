package io.dpcaio.app

import android.content.Context
import android.content.Intent

sealed interface EnrollmentExecutionOutcome {
    data object Complete : EnrollmentExecutionOutcome
    data class Retryable(val message: String) : EnrollmentExecutionOutcome
    data class Failed(val message: String) : EnrollmentExecutionOutcome
}

class EnrollmentExecutionRouter(private val context: Context) {
    fun execute(intent: Intent): EnrollmentExecutionOutcome {
        val config = EnrollmentConfigParser.parse(extractAdminExtras(intent)).config
        return when (config.offlineMode) {
            "FULL_OFFLINE", "OFFLINE_THEN_SYNC" -> when (val result = OfflineEnrollmentCoordinator(context).resumeOrCreate(intent, config)) {
                is OfflineEnrollmentOutcome.Complete -> EnrollmentExecutionOutcome.Complete
                is OfflineEnrollmentOutcome.Retryable -> EnrollmentExecutionOutcome.Retryable(result.message)
                is OfflineEnrollmentOutcome.Failed -> EnrollmentExecutionOutcome.Failed(result.message)
            }
            else -> when (val result = EnrollmentCoordinator(context).resumeOrCreate(intent)) {
                is EnrollmentOutcome.Complete -> EnrollmentExecutionOutcome.Complete
                is EnrollmentOutcome.Retryable -> EnrollmentExecutionOutcome.Retryable(result.message)
                is EnrollmentOutcome.Failed -> EnrollmentExecutionOutcome.Failed(result.message)
            }
        }
    }

    private fun extractAdminExtras(intent: Intent): android.os.PersistableBundle? =
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
}
