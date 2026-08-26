package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.location.LocationManager
import android.os.Build
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

class AndroidGlobalLocationPolicyGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    fun read(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 28) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Location policy requires API 28+")
        return runCatching { PolicyResult.success(locationManager.isLocationEnabled) }
            .getOrElse { PolicyResult.failure(PolicyStatus.FAILED, "LOCATION_READBACK_FAILED:${it.javaClass.simpleName}", it.javaClass.name) }
    }

    fun set(enabled: Boolean): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 28) return PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Location policy requires API 28+")
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return PolicyResult.failure(PolicyStatus.NOT_DEVICE_OWNER, "DEVICE_OWNER_REQUIRED")
        return try {
            dpm.setLocationEnabled(admin, enabled)
            val observed = locationManager.isLocationEnabled
            if (observed == enabled) PolicyResult.success(observed, "READBACK_VERIFIED")
            else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "LOCATION_READBACK_MISMATCH")
        } catch (e: SecurityException) {
            PolicyResult.failure(PolicyStatus.SECURITY_EXCEPTION, "LOCATION_SECURITY_EXCEPTION", e.javaClass.name)
        } catch (e: RuntimeException) {
            PolicyResult.failure(PolicyStatus.FAILED, "LOCATION_CALL_FAILED:${e.javaClass.simpleName}", e.javaClass.name)
        }
    }
}
