package io.dpcaio.app

import android.content.Context
import io.dpcaio.appmanager.AppPolicyCoordinator
import io.dpcaio.appmanager.AppPolicyVerification
import io.dpcaio.knox.license.KnoxPackageControlRoute
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

data class KnoxAwarePackageControlResult(
    val route: KnoxPackageControlRoute,
    val success: Boolean,
    val detail: String? = null
)

class KnoxAwarePackageController(context: Context) {
    private val app = context.applicationContext
    private val coordinator = AppPolicyCoordinator(
        AndroidDevicePolicyGateway(app, AioDeviceAdminReceiver.componentName(app))
    )

    fun disableLike(packageName: String): KnoxAwarePackageControlResult {
        val runtime = KnoxRuntimeStateStore.read(app)?.access
            ?: KnoxStartupController.evaluateAndPersist(app).access

        if (runtime.allowDpmPackageControl) {
            val hidden = coordinator.setHidden(packageName, true)
            if (hidden.verification == AppPolicyVerification.VERIFIED) {
                return KnoxAwarePackageControlResult(KnoxPackageControlRoute.DPM_HIDE, true)
            }

            val suspended = coordinator.setSuspended(packageName, true)
            if (suspended.verification == AppPolicyVerification.VERIFIED) {
                return KnoxAwarePackageControlResult(KnoxPackageControlRoute.DPM_SUSPEND, true)
            }
        }

        return KnoxAwarePackageControlResult(
            route = KnoxPackageControlRoute.REAL_KNOX_REQUIRED,
            success = false,
            detail = "Public DPM hide/suspend routes failed; this operation needs a real Knox-authorized route"
        )
    }

    fun enableLike(packageName: String): KnoxAwarePackageControlResult {
        val unhidden = coordinator.setHidden(packageName, false)
        if (unhidden.verification == AppPolicyVerification.VERIFIED) {
            coordinator.setSuspended(packageName, false)
            return KnoxAwarePackageControlResult(KnoxPackageControlRoute.DPM_HIDE, true)
        }
        val unsuspended = coordinator.setSuspended(packageName, false)
        return KnoxAwarePackageControlResult(
            route = KnoxPackageControlRoute.DPM_SUSPEND,
            success = unsuspended.verification == AppPolicyVerification.VERIFIED,
            detail = unsuspended.message
        )
    }
}
